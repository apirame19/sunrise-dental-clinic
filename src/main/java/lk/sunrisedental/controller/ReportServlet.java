package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.model.User;
import lk.sunrisedental.patterns.composite.ReportSection;

import java.io.IOException;
import java.time.LocalDate;

/**
 * The reporting screen: {@code GET /app/reports?type=...}.
 *
 * <p>One servlet and one view cover all six reports. That is possible because every report is a
 * {@link ReportSection} tree and the view renders a tree with one recursive fragment - the daily
 * report is three levels deep, the billing summary two, the clinic overview four, and the JSP does
 * not know or care.</p>
 *
 * <p><strong>No authorisation decision is made here.</strong> The facade knows which reports carry
 * revenue and refuses them to a role that may not see takings. Repeating that judgement in this
 * servlet would create a second copy of the rule that could be updated without the first, and the
 * JSON reports endpoint would then be a third. There is one rule, in one place, and this servlet
 * simply asks for a report by name.</p>
 */
@WebServlet(name = "ReportServlet", urlPatterns = {"/app/reports", "/app/reports/*"})
public class ReportServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        String type = param(request, "type") == null ? "daily" : param(request, "type");

        LocalDate today = facade().today();
        LocalDate from = dateParam(request, "from", defaultFrom(type, today));
        LocalDate to = dateParam(request, "to", today);

        // A range entered backwards is a slip, not an attack. Swapping it silently is friendlier
        // than an error page and cannot produce a wrong answer.
        if (to.isBefore(from)) {
            LocalDate swapped = from;
            from = to;
            to = swapped;
        }

        Integer dentistId = optionalIntParam(request, "dentistId");

        ReportSection report = facade().report(actor, type, from, to, dentistId);

        request.setAttribute("report", report);
        request.setAttribute("reportType", type);
        request.setAttribute("from", from);
        request.setAttribute("to", to);
        request.setAttribute("dentistId", dentistId);
        request.setAttribute("dentists", facade().dentists(actor, false));
        request.setAttribute("currency", facade().configuration().getCurrencyCode());
        request.setAttribute("singleDate", isSingleDate(type));

        render(request, response, "reports/view", report.getTitle(),
                "/app/reports?type=" + type);
    }

    /**
     * @return the start date to use when none was given. A period report defaults to the last
     *         month, because a one-day revenue report is almost never the question being asked;
     *         a daily report defaults to today, because that one always is.
     */
    private static LocalDate defaultFrom(String type, LocalDate today) {
        return isSingleDate(type) ? today : today.minusMonths(1);
    }

    /** @return {@code true} for the reports that cover one day rather than a range. */
    private static boolean isSingleDate(String type) {
        return "daily".equals(type) || "overview".equals(type);
    }
}
