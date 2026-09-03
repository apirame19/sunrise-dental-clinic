package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.facade.DashboardSnapshot;

import java.io.IOException;

/**
 * The landing screen: {@code GET /app/dashboard}.
 *
 * <p>One facade call produces everything on the page - the day's figures, the appointment list and
 * the reminder queue. That is the whole reason {@link DashboardSnapshot} exists: a dashboard
 * assembled from nine separate calls would issue nine round trips and, worse, could show figures
 * taken at nine different moments.</p>
 *
 * <p>Whether the takings tile appears is decided in the business tier, not here. The snapshot
 * carries {@code null} revenue for a role that may not see it, so this servlet has no rule to get
 * wrong and the JSON channel cannot answer the question differently.</p>
 */
@WebServlet(name = "DashboardServlet", urlPatterns = "/app/dashboard")
public class DashboardServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // A malformed ?date= shows today rather than an error page: a bad query string is not
        // worth interrupting somebody's morning for.
        DashboardSnapshot snapshot = facade().dashboard(
                currentUser(request),
                dateParam(request, "date", facade().today()));

        request.setAttribute("snapshot", snapshot);

        render(request, response, "dashboard", "Dashboard", "/app/dashboard");
    }
}
