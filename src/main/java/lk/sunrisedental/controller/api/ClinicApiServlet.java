package lk.sunrisedental.controller.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.controller.Csrf;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.model.AvailabilitySlot;
import lk.sunrisedental.model.Dentist;
import lk.sunrisedental.model.Treatment;
import lk.sunrisedental.model.User;
import lk.sunrisedental.util.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * The reference data an integration needs before it can book anything: {@code /api/clinic/*}.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code GET /api/clinic/session}</td><td>who is signed in, and the CSRF token</td></tr>
 *   <tr><td>{@code GET /api/clinic/dentists}</td><td>the roster</td></tr>
 *   <tr><td>{@code GET /api/clinic/treatments}</td><td>the catalogue and prices</td></tr>
 *   <tr><td>{@code GET /api/clinic/availability?dentistId=&date=}</td><td>free and taken slots</td></tr>
 *   <tr><td>{@code GET /api/clinic/settings}</td><td>opening hours, fees, booking window</td></tr>
 * </table>
 *
 * <p><strong>Why {@code session} returns the CSRF token.</strong> These endpoints authenticate
 * with the ordinary session cookie, which browsers attach to any request to this origin - so
 * without a token a page on another site could post to {@code /api/appointments} on a signed-in
 * receptionist's behalf. The same synchroniser token the HTML forms carry therefore guards the
 * API's writes too, and this endpoint is how a same-origin caller obtains it. It is deliberately a
 * {@code GET}: an attacker's page can cause the request but cannot read the response, because the
 * same-origin policy stops it.</p>
 *
 * <p>{@code settings} exposes the clinic's operating parameters - opening hours, consultation fee,
 * levy, booking window - so a caller can present a sensible booking form without hard-coding
 * numbers that live in the database. It exposes no credentials and no patient data.</p>
 */
@WebServlet(name = "ClinicApiServlet", urlPatterns = {"/api/clinic", "/api/clinic/*"})
public class ClinicApiServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User actor = currentUser(request);

        switch (action(request)) {
            case "session" -> session(request, response, actor);
            case "dentists" -> dentists(request, response, actor);
            case "treatments" -> treatments(request, response, actor);
            case "availability" -> availability(request, response, actor);
            case "settings" -> settings(response);
            default -> throw new BusinessRuleException("NO_SUCH_ENDPOINT",
                    "There is no clinic endpoint at that address. Try session, dentists, "
                    + "treatments, availability or settings.");
        }
    }

    private void session(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        ok(response, JsonWriter.object()
                .add("user", JsonModels.user(actor))
                .add("csrfToken", Csrf.token(request.getSession(true)))
                .add("today", facade().today().toString()));
    }

    private void dentists(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        boolean activeOnly = !"false".equalsIgnoreCase(param(request, "activeOnly"));
        List<Dentist> dentists = facade().dentists(actor, activeOnly);

        ok(response, JsonWriter.object()
                .add("activeOnly", activeOnly)
                .add("count", dentists.size())
                .add("dentists", JsonModels.arrayOf(dentists, JsonModels::dentist)));
    }

    private void treatments(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        boolean activeOnly = !"false".equalsIgnoreCase(param(request, "activeOnly"));
        List<Treatment> treatments = facade().treatments(actor, activeOnly);

        JsonWriter.JsonArray array = JsonWriter.array();
        for (Treatment treatment : treatments) {
            // The indicative total comes from the business tier. A caller working it out from
            // baseCost plus a fee it guessed would be a second implementation of the pricing rules.
            array.add(JsonModels.treatment(treatment)
                    .add("indicativeTotal", facade().indicativeTotal(treatment)));
        }

        ok(response, JsonWriter.object()
                .add("activeOnly", activeOnly)
                .add("count", treatments.size())
                .add("treatments", array));
    }

    private void availability(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        Integer dentistId = optionalIntParam(request, "dentistId");
        if (dentistId == null) {
            throw new BusinessRuleException("BAD_REQUEST", "Give the dentist id as ?dentistId=");
        }
        LocalDate date = dateParam(request, "date", facade().today());

        List<AvailabilitySlot> slots = facade().dentistAvailability(actor, dentistId, date);

        ok(response, JsonWriter.object()
                .add("dentist", JsonModels.dentist(facade().findDentist(actor, dentistId)))
                .add("date", date.toString())
                .add("freeSlots", slots.stream().filter(AvailabilitySlot::available).count())
                .add("slots", JsonModels.arrayOf(slots, JsonModels::slot)));
    }

    private void settings(HttpServletResponse response) throws IOException {
        var configuration = facade().configuration();

        ok(response, JsonWriter.object()
                .add("clinicName", configuration.getClinicName())
                .add("address", configuration.getClinicAddress())
                .add("phone", configuration.getClinicPhone())
                .add("currency", configuration.getCurrencyCode())
                .add("openingTime", configuration.getOpeningTime().toString())
                .add("closingTime", configuration.getClosingTime().toString())
                .add("slotMinutes", configuration.getSlotMinutes())
                .add("closedWeekday", configuration.getClosedWeekday())
                .add("maxBookingDaysAhead", configuration.getMaxBookingDaysAhead())
                .add("consultationFee", configuration.getConsultationFee())
                .add("taxRatePercent", configuration.getTaxRatePercent())
                .add("followUpDiscountPercent", configuration.getFollowUpDiscountPercent())
                .add("followUpWindowDays", configuration.getFollowUpWindowDays()));
    }
}
