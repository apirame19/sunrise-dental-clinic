package lk.sunrisedental.controller.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.patterns.singleton.DatabaseConnectionManager;
import lk.sunrisedental.util.JsonWriter;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Liveness endpoint: {@code GET /api/health}.
 *
 * <p>This is the one {@code /api/*} endpoint deliberately left unauthenticated, so that a
 * monitoring tool or a deployment script can confirm the application is up without holding
 * credentials. It exposes no clinic data: counts, names, figures and configuration all live behind
 * a session.</p>
 *
 * <p>It extends {@link HttpServlet} rather than {@link ApiServlet} for exactly that reason -
 * {@code ApiServlet} refuses an anonymous caller, which is the correct default and the one thing
 * this endpoint must not do.</p>
 *
 * <p>Database reachability is reported, and an unreachable database produces {@code 503} rather
 * than {@code 200} with a flag buried in the body. A health check that returns "OK" while the
 * application cannot serve a single page is worse than no health check, because something is
 * relying on it.</p>
 */
@WebServlet(name = "HealthApiServlet", urlPatterns = "/api/health")
public class HealthApiServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String APPLICATION_NAME = "Sunrise Dental Clinic";
    private static final String APPLICATION_VERSION = "1.0.0-SNAPSHOT";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        DatabaseConnectionManager connections = DatabaseConnectionManager.getInstance();
        boolean databaseReachable = connections.isDatabaseReachable();

        response.setStatus(databaseReachable
                ? HttpServletResponse.SC_OK
                : HttpServletResponse.SC_SERVICE_UNAVAILABLE);

        String body = JsonWriter.object()
                .add("status", databaseReachable ? "UP" : "DEGRADED")
                .add("application", APPLICATION_NAME)
                .add("version", APPLICATION_VERSION)
                .add("databaseReachable", databaseReachable)
                .add("poolSize", connections.getPoolSize())
                .add("connectionsInUse", connections.getLiveConnectionCount())
                .add("connectionsIdle", connections.getIdleConnectionCount())
                .add("servletApi", getServletContext().getMajorVersion()
                        + "." + getServletContext().getMinorVersion())
                .add("javaVersion", System.getProperty("java.version"))
                .toJson();

        try (PrintWriter out = response.getWriter()) {
            out.print(body);
        }
    }
}
