package lk.sunrisedental.controller.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AppointmentForm;
import lk.sunrisedental.model.AppointmentStatus;
import lk.sunrisedental.model.User;
import lk.sunrisedental.util.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Appointments over JSON: {@code /api/appointments/*}.
 *
 * <p>Every operation here goes through the same facade methods the HTML screens use, so the
 * duplicate-number check, the double-booking check, the validation chain and the status lifecycle
 * all apply identically. An API that bypassed them to "keep the integration simple" would be a
 * second, weaker front door to the same data.</p>
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code GET  /api/appointments?date=}</td><td>the day's list</td></tr>
 *   <tr><td>{@code GET  /api/appointments?from=&to=}</td><td>a range</td></tr>
 *   <tr><td>{@code GET  /api/appointments/search?no=}</td><td>one appointment, or 404</td></tr>
 *   <tr><td>{@code GET  /api/appointments/next-number?date=}</td><td>a free appointment number</td></tr>
 *   <tr><td>{@code POST /api/appointments}</td><td>register one</td></tr>
 *   <tr><td>{@code POST /api/appointments/status}</td><td>move it through its lifecycle</td></tr>
 * </table>
 *
 * <p>Submissions are {@code application/x-www-form-urlencoded} rather than a JSON body. The
 * application has no JSON parser and does not need one: {@code request.getParameter} is already
 * there, it is what the HTML forms post, and adding a parser dependency to read six fields would
 * be more machinery than the problem justifies.</p>
 */
@WebServlet(name = "AppointmentApiServlet",
        urlPatterns = {"/api/appointments", "/api/appointments/*"})
public class AppointmentApiServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User actor = currentUser(request);

        switch (action(request)) {
            case "search" -> search(request, response, actor);
            case "next-number" -> nextNumber(request, response);
            case "" -> list(request, response, actor);
            default -> throw notFound();
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User actor = currentUser(request);

        switch (action(request)) {
            case "status" -> updateStatus(request, response, actor);
            case "" -> register(request, response, actor);
            default -> throw notFound();
        }
    }

    // ------------------------------------------------------------------ reads

    private void list(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        LocalDate from = dateParam(request, "from", null);
        LocalDate to = dateParam(request, "to", null);

        List<Appointment> appointments;
        String scope;

        if (from != null && to != null) {
            appointments = facade().appointmentsBetween(actor, from, to);
            scope = from + " to " + to;
        } else {
            LocalDate date = dateParam(request, "date", facade().today());
            appointments = facade().appointmentsOn(actor, date);
            scope = date.toString();
        }

        ok(response, JsonWriter.object()
                .add("scope", scope)
                .add("count", appointments.size())
                .add("appointments", JsonModels.arrayOf(appointments, JsonModels::appointment)));
    }

    private void search(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        String appointmentNo = param(request, "no");
        if (appointmentNo == null) {
            throw badRequest("Give the appointment number as ?no=");
        }

        Optional<Appointment> found = facade().searchAppointment(actor, appointmentNo);

        if (found.isEmpty()) {
            // 404 rather than an empty 200. A caller asked for a specific record by its
            // identifier; "here is nothing" and "that does not exist" are different answers.
            write(response, HttpServletResponse.SC_NOT_FOUND, JsonWriter.object()
                    .add("error", "APPOINTMENT_NOT_FOUND")
                    .add("message", "No appointment was found with the number "
                            + appointmentNo + "."));
            return;
        }

        Appointment appointment = found.get();
        ok(response, JsonWriter.object()
                .add("appointment", JsonModels.appointment(appointment))
                .add("bill", JsonModels.bill(
                        facade().findBillForAppointment(actor, appointment.getAppointmentNo())
                                .orElse(null))));
    }

    private void nextNumber(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        LocalDate date = dateParam(request, "date", facade().today());

        ok(response, JsonWriter.object()
                .add("date", date.toString())
                .add("appointmentNo", facade().suggestAppointmentNo(date)));
    }

    // ------------------------------------------------------------------ writes

    private void register(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

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

        Appointment saved = facade().registerAppointment(actor, form);

        // 201 with the created resource. A duplicate number or a busy dentist arrives as a
        // ClinicException and is turned into 400 by ApiServlet, carrying its stable error code.
        write(response, HttpServletResponse.SC_CREATED, JsonWriter.object()
                .add("appointment", JsonModels.appointment(saved)));
    }

    private void updateStatus(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        String appointmentNo = param(request, "no");
        String requested = param(request, "status");

        if (appointmentNo == null) {
            throw badRequest("Give the appointment number as no=");
        }

        AppointmentStatus newStatus = AppointmentStatus.fromString(requested)
                .orElseThrow(() -> badRequest("'" + requested + "' is not an appointment status. "
                        + "Use SCHEDULED, COMPLETED, CANCELLED or NO_SHOW."));

        Appointment updated = facade().updateAppointmentStatus(
                actor, appointmentNo, newStatus, param(request, "reason"));

        ok(response, JsonWriter.object()
                .add("appointment", JsonModels.appointment(updated)));
    }

    // ------------------------------------------------------------------ helpers

    private static lk.sunrisedental.exception.BusinessRuleException badRequest(String message) {
        return new lk.sunrisedental.exception.BusinessRuleException("BAD_REQUEST", message);
    }

    private static lk.sunrisedental.exception.BusinessRuleException notFound() {
        return new lk.sunrisedental.exception.BusinessRuleException("NO_SUCH_ENDPOINT",
                "There is no appointment endpoint at that address.");
    }
}
