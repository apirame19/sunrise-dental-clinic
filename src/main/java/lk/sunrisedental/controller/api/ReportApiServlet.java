package lk.sunrisedental.controller.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.model.Reminder;
import lk.sunrisedental.model.User;
import lk.sunrisedental.patterns.composite.ReportSection;
import lk.sunrisedental.service.ReportService;
import lk.sunrisedental.util.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Reports and the reminder queue over JSON: {@code /api/reports/*}.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code GET /api/reports}</td><td>which reports exist</td></tr>
 *   <tr><td>{@code GET /api/reports/run?type=&from=&to=&dentistId=}</td><td>run one</td></tr>
 *   <tr><td>{@code GET /api/reports/patient-history?id=}</td><td>a patient's record as a tree</td></tr>
 *   <tr><td>{@code GET /api/reports/reminders?days=}</td><td>the reminder queue</td></tr>
 *   <tr><td>{@code GET /api/reports/dashboard?date=}</td><td>the dashboard figures</td></tr>
 * </table>
 *
 * <p>The report tree is serialised by recursion, exactly as the JSP renders it, so the JSON
 * carries the same subtotal at every level that the screen shows - derived from the children
 * rather than accumulated beside them.</p>
 *
 * <p><strong>No authorisation decision is made here.</strong> Which reports carry revenue, and who
 * may see them, is settled once inside the facade. That is the entire reason both channels call
 * it: if this servlet made its own judgement, the JSON endpoint could hand a receptionist the
 * takings that the HTML screen refuses them.</p>
 */
@WebServlet(name = "ReportApiServlet", urlPatterns = {"/api/reports", "/api/reports/*"})
public class ReportApiServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    private static final int MAX_LOOK_AHEAD_DAYS = 30;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User actor = currentUser(request);

        switch (action(request)) {
            case "run" -> run(request, response, actor);
            case "patient-history" -> patientHistory(request, response, actor);
            case "reminders" -> reminders(request, response, actor);
            case "dashboard" -> dashboard(request, response, actor);
            case "" -> index(response);
            default -> throw new BusinessRuleException("NO_SUCH_ENDPOINT",
                    "There is no report endpoint at that address.");
        }
    }

    /** @return the report catalogue, so a caller need not hard-code the type names. */
    private void index(HttpServletResponse response) throws IOException {
        JsonWriter.JsonArray types = JsonWriter.array();
        ReportService.availableReportTypes().forEach(types::add);

        ok(response, JsonWriter.object()
                .add("reportTypes", types)
                .add("note", "Run one with /api/reports/run?type=daily&from=&to=. "
                        + "Reports carrying revenue are refused to roles that may not see it."));
    }

    private void run(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        String type = param(request, "type") == null ? "daily" : param(request, "type");
        LocalDate today = facade().today();

        LocalDate from = dateParam(request, "from", today);
        LocalDate to = dateParam(request, "to", today);
        if (to.isBefore(from)) {
            LocalDate swapped = from;
            from = to;
            to = swapped;
        }

        ReportSection report = facade().report(
                actor, type, from, to, optionalIntParam(request, "dentistId"));

        ok(response, JsonWriter.object()
                .add("type", type)
                .add("from", from.toString())
                .add("to", to.toString())
                .add("report", JsonModels.report(report)));
    }

    private void patientHistory(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        Integer patientId = optionalIntParam(request, "id");
        if (patientId == null) {
            throw new BusinessRuleException("BAD_REQUEST", "Give the patient id as ?id=");
        }

        ok(response, JsonWriter.object()
                .add("patientId", patientId)
                .add("report", JsonModels.report(facade().patientHistoryReport(actor, patientId))));
    }

    private void reminders(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        Integer requested = optionalIntParam(request, "days");
        int days = requested == null
                ? 1
                : Math.min(MAX_LOOK_AHEAD_DAYS, Math.max(1, requested));

        List<Reminder> queue = facade().reminderQueue(actor, days);

        ok(response, JsonWriter.object()
                .add("lookAheadDays", days)
                .add("count", queue.size())
                .add("overdueCount", queue.stream().filter(Reminder::isOverdue).count())
                .add("reminders", JsonModels.arrayOf(queue, JsonModels::reminder)));
    }

    private void dashboard(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        var snapshot = facade().dashboard(actor, dateParam(request, "date", facade().today()));

        JsonWriter.JsonObject json = JsonWriter.object()
                .add("date", snapshot.date().toString())
                .add("todayTotal", snapshot.todayTotal())
                .add("todayScheduled", snapshot.todayScheduled())
                .add("todayCompleted", snapshot.todayCompleted())
                .add("todayCancelled", snapshot.todayCancelled())
                .add("todayNoShow", snapshot.todayNoShow())
                .add("totalPatients", snapshot.totalPatients())
                .add("activeDentists", snapshot.activeDentists())
                .add("upcomingScheduled", snapshot.upcomingScheduled())
                .add("awaitingOutcome", snapshot.awaitingOutcomeCount());

        // Absent rather than zero for a role that may not see takings. Zero is a number the
        // caller would reasonably believe.
        if (snapshot.revenueVisible()) {
            json.add("todayRevenue", snapshot.todayRevenue());
        }

        ok(response, json
                .add("appointments",
                        JsonModels.arrayOf(snapshot.todayAppointments(), JsonModels::appointment))
                .add("reminders",
                        JsonModels.arrayOf(snapshot.reminders(), JsonModels::reminder)));
    }
}
