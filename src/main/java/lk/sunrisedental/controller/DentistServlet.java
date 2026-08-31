package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.model.Dentist;
import lk.sunrisedental.model.User;

import java.io.IOException;
import java.time.LocalDate;

/**
 * The practice roster: {@code /app/dentists/*}.
 *
 * <p>Two audiences share this servlet. Anyone signed in may look at who works here and at a
 * dentist's day - the front desk needs that to answer the telephone. Only an administrator may add
 * a dentist, amend one or withdraw one, which the authorisation filter enforces by path and the
 * facade enforces again at the operation.</p>
 *
 * <p>The schedule view shows availability slot by slot rather than as a list of bookings. A list
 * answers "what is booked"; the receptionist's actual question is "when is this dentist free", and
 * the slot view answers it using the same interval-overlap rule the booking form applies - so a
 * 90-minute treatment at 10:00 shows 10:30 as taken on both screens.</p>
 */
@WebServlet(name = "DentistServlet", urlPatterns = {"/app/dentists", "/app/dentists/*"})
public class DentistServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        switch (action(request)) {
            case "schedule" -> showSchedule(request, response);
            case "add" -> showAddForm(request, response);
            case "edit" -> showEditForm(request, response);
            case "" -> showRoster(request, response);
            default -> throw badRequest("There is no dentist page at that address.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        switch (action(request)) {
            case "add" -> add(request, response);
            case "edit" -> update(request, response);
            case "activate" -> setActive(request, response);
            default -> throw badRequest("That dentist action is not recognised.");
        }
    }

    // ------------------------------------------------------------------ roster and schedule

    private void showRoster(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // false: withdrawn dentists are shown too, greyed out. They still own historical
        // appointments, and a roster that hides them makes those records look orphaned.
        request.setAttribute("dentists", facade().dentists(currentUser(request), false));

        render(request, response, "dentists/list", "Dentists", "/app/dentists");
    }

    private void showSchedule(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        int dentistId = requiredId(request);
        LocalDate date = dateParam(request, "date", facade().today());

        request.setAttribute("dentist", facade().findDentist(actor, dentistId));
        request.setAttribute("date", date);
        request.setAttribute("previousDate", date.minusDays(1));
        request.setAttribute("nextDate", date.plusDays(1));
        request.setAttribute("appointments", facade().dentistSchedule(actor, dentistId, date));
        request.setAttribute("slots", facade().dentistAvailability(actor, dentistId, date));

        render(request, response, "dentists/schedule", "Dentist schedule", "/app/dentists");
    }

    // ------------------------------------------------------------------ master data

    private void showAddForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        render(request, response, "dentists/form", "Add a dentist", "/app/dentists");
    }

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setAttribute("dentist",
                facade().findDentist(currentUser(request), requiredId(request)));
        render(request, response, "dentists/form", "Amend dentist", "/app/dentists");
    }

    private void add(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Dentist submitted = readForm(request, 0);

        try {
            Dentist saved = facade().addDentist(currentUser(request), submitted);
            flash(request, true, saved.getDentistName() + " has been added to the practice.");
            redirect(request, response, "/app/dentists");

        } catch (ValidationException e) {
            redisplay(request, response, e, submitted, "Add a dentist");
        }
    }

    private void update(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Dentist submitted = readForm(request, requiredId(request));

        try {
            Dentist saved = facade().updateDentist(currentUser(request), submitted);
            flash(request, true, "The record for " + saved.getDentistName() + " has been updated.");
            redirect(request, response, "/app/dentists");

        } catch (ValidationException e) {
            redisplay(request, response, e, submitted, "Amend dentist");
        }
    }

    /**
     * Withdraws a dentist from the booking list, or reinstates one.
     *
     * <p>Withdrawal is refused by the business tier while the dentist still has appointments
     * ahead of them. That refusal arrives here as a {@link BusinessRuleException}, and it is
     * caught rather than allowed to become an error page: the administrator needs to stay on the
     * roster to go and deal with those appointments.</p>
     */
    private void setActive(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        int dentistId = requiredId(request);
        boolean active = Boolean.parseBoolean(param(request, "active"));

        try {
            Dentist saved = facade().setDentistActive(currentUser(request), dentistId, active);
            flash(request, true, saved.getDentistName()
                    + (active ? " has been reinstated." : " has been withdrawn from booking."));

        } catch (BusinessRuleException e) {
            flash(request, false, e.getUserMessage());
        }

        redirect(request, response, "/app/dentists");
    }

    private void redisplay(HttpServletRequest request, HttpServletResponse response,
                           ValidationException e, Dentist submitted, String title)
            throws ServletException, IOException {

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        request.setAttribute("validation", e.getValidationResult());
        request.setAttribute("dentist", submitted);
        render(request, response, "dentists/form", title, "/app/dentists");
    }

    private static Dentist readForm(HttpServletRequest request, int dentistId) {
        Dentist dentist = new Dentist();
        dentist.setDentistId(dentistId);
        dentist.setDentistName(param(request, "dentistName"));
        dentist.setSpecialization(param(request, "specialization"));
        dentist.setLicenseNo(param(request, "licenseNo"));
        dentist.setContactNumber(param(request, "contactNumber"));
        return dentist;
    }

    private static int requiredId(HttpServletRequest request) {
        Integer dentistId = optionalIntParam(request, "id");
        if (dentistId == null) {
            throw new BusinessRuleException("BAD_REQUEST", "Please give a dentist.");
        }
        return dentistId;
    }
}
