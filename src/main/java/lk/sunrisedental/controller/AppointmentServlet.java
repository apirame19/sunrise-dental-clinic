package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.exception.DoubleBookingException;
import lk.sunrisedental.exception.DuplicateAppointmentException;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AppointmentForm;
import lk.sunrisedental.model.AppointmentStatus;
import lk.sunrisedental.model.User;
import lk.sunrisedental.validation.ValidationResult;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * The appointment screens: {@code /app/appointments/*}.
 *
 * <p>One servlet covers registration, search, the day list, the details view and status changes,
 * dispatched on the path after the mapping. They are one servlet because they are one workflow -
 * a receptionist books, then searches for what they booked, then records how it went - and
 * splitting them across five classes would mean five copies of the same dropdown loading and the
 * same error handling.</p>
 *
 * <p><strong>Every rejection redisplays the form with the typing intact.</strong> Three different
 * failures can come back from a booking - a field the validation chain refused, an appointment
 * number already in use, and a dentist who is not free - and all three are caught here rather than
 * being left to {@code BaseServlet}'s error page. A receptionist who has typed a patient's name,
 * address and telephone number must not lose them because they picked a busy slot.</p>
 *
 * <p>Every state change is a {@code POST} followed by a redirect. That is what stops a browser
 * refresh registering the same appointment twice, which would defeat the duplicate checks by
 * creating two genuinely distinct bookings.</p>
 */
@WebServlet(name = "AppointmentServlet", urlPatterns = {"/app/appointments", "/app/appointments/*"})
public class AppointmentServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        switch (action(request)) {
            case "register" -> showRegisterForm(request, response);
            case "day" -> showDay(request, response);
            case "view" -> showDetails(request, response);
            case "", "search" -> showSearch(request, response);
            default -> throw badRequest("There is no appointment page at that address.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        switch (action(request)) {
            case "register" -> register(request, response);
            case "status" -> updateStatus(request, response);
            default -> throw badRequest("That appointment action is not recognised.");
        }
    }

    // ------------------------------------------------------------------ registration

    private void showRegisterForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        LocalDate date = dateParam(request, "date", facade().today());

        AppointmentForm form = new AppointmentForm();
        form.setAppointmentDate(date.toString());
        // Suggested, not imposed: the number is an editable field so the clinic can follow its own
        // numbering when it needs to.
        form.setAppointmentNo(facade().suggestAppointmentNo(date));

        request.setAttribute("form", form);
        renderRegisterForm(request, response, actor);
    }

    private void register(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        AppointmentForm form = readForm(request);

        try {
            Appointment saved = facade().registerAppointment(actor, form);

            flash(request, true, "Appointment " + saved.getAppointmentNo() + " registered for "
                    + saved.getPatient().getPatientName() + " with "
                    + saved.getDentist().getDentistName() + ".");

            redirect(request, response,
                    "/app/appointments/view?no=" + encode(saved.getAppointmentNo()));

        } catch (ValidationException e) {
            // Every bad field at once, each beside its own input.
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("validation", e.getValidationResult());
            request.setAttribute("form", form);
            renderRegisterForm(request, response, actor);

        } catch (DuplicateAppointmentException | DoubleBookingException e) {
            // Not field errors - they are facts about the clinic's diary rather than about what
            // was typed - so they are shown as a banner, with the form still populated.
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            ValidationResult result = new ValidationResult();
            result.addGlobalError(e.getUserMessage());
            request.setAttribute("validation", result);
            request.setAttribute("form", form);
            renderRegisterForm(request, response, actor);
        }
    }

    /** Loads the dropdowns and shows the booking form. */
    private void renderRegisterForm(HttpServletRequest request, HttpServletResponse response,
                                    User actor) throws ServletException, IOException {

        // Active only: a withdrawn dentist or a discontinued treatment must not be bookable, and
        // the validation chain refuses them anyway.
        request.setAttribute("dentists", facade().dentists(actor, true));
        request.setAttribute("treatments", facade().treatments(actor, true));
        request.setAttribute("clinicOpen", facade().configuration().getOpeningTime());
        request.setAttribute("clinicClose", facade().configuration().getClosingTime());
        request.setAttribute("maxDaysAhead", facade().configuration().getMaxBookingDaysAhead());
        request.setAttribute("today", facade().today());

        render(request, response, "appointments/register", "Register appointment",
                "/app/appointments/register");
    }

    /** @return the submitted values, trimmed, exactly as the validation chain expects them. */
    private static AppointmentForm readForm(HttpServletRequest request) {
        AppointmentForm form = new AppointmentForm();
        form.setAppointmentNo(request.getParameter("appointmentNo"));
        form.setPatientName(request.getParameter("patientName"));
        form.setAddress(request.getParameter("address"));
        form.setContactNumber(request.getParameter("contactNumber"));
        form.setDentistId(request.getParameter("dentistId"));
        form.setTreatmentId(request.getParameter("treatmentId"));
        form.setAppointmentDate(request.getParameter("appointmentDate"));
        form.setAppointmentTime(request.getParameter("appointmentTime"));
        form.setNotes(request.getParameter("notes"));
        form.normalise();
        return form;
    }

    // ------------------------------------------------------------------ search and lists

    private void showSearch(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        String appointmentNo = param(request, "no");

        if (appointmentNo != null) {
            Optional<Appointment> found = facade().searchAppointment(actor, appointmentNo);
            request.setAttribute("searchTerm", appointmentNo);
            // An appointment that does not exist is an ordinary search outcome, not an error.
            // Sending the user to a 404 page for it would be absurd.
            request.setAttribute("appointment", found.orElse(null));
            request.setAttribute("searched", Boolean.TRUE);

            found.ifPresent(appointment -> request.setAttribute("bill",
                    facade().findBillForAppointment(actor, appointment.getAppointmentNo())
                            .orElse(null)));
        }

        render(request, response, "appointments/search", "Search appointments",
                "/app/appointments/search");
    }

    private void showDay(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        LocalDate date = dateParam(request, "date", facade().today());

        request.setAttribute("date", date);
        request.setAttribute("previousDate", date.minusDays(1));
        request.setAttribute("nextDate", date.plusDays(1));
        request.setAttribute("appointments", facade().appointmentsOn(actor, date));

        render(request, response, "appointments/day", "Appointments for the day",
                "/app/appointments/day");
    }

    private void showDetails(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        String appointmentNo = param(request, "no");
        if (appointmentNo == null) {
            throw badRequest("Please give an appointment number.");
        }

        Appointment appointment = facade().findAppointment(actor, appointmentNo);

        request.setAttribute("appointment", appointment);
        request.setAttribute("allowedTransitions", appointment.getStatus().allowedTransitions());
        request.setAttribute("bill",
                facade().findBillForAppointment(actor, appointmentNo).orElse(null));

        render(request, response, "appointments/view",
                "Appointment " + appointment.getAppointmentNo(), "/app/appointments/search");
    }

    // ------------------------------------------------------------------ status lifecycle

    private void updateStatus(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User actor = currentUser(request);
        String appointmentNo = param(request, "no");
        String requested = param(request, "status");

        if (appointmentNo == null) {
            throw badRequest("Please give an appointment number.");
        }

        AppointmentStatus newStatus = AppointmentStatus.fromString(requested)
                .orElseThrow(() -> badRequest("'" + requested + "' is not an appointment status."));

        Appointment updated = facade().updateAppointmentStatus(
                actor, appointmentNo, newStatus, param(request, "reason"));

        flash(request, true, "Appointment " + updated.getAppointmentNo() + " is now "
                + updated.getStatus().getLabel().toLowerCase() + ".");

        redirect(request, response, "/app/appointments/view?no=" + encode(appointmentNo));
    }
}
