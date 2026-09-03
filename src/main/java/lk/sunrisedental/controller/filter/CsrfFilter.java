package lk.sunrisedental.controller.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.controller.Csrf;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Rejects any state-changing submission that does not carry this session's synchroniser token.
 *
 * <p>Only {@code POST} is checked. Every request that changes clinic data in this application is a
 * {@code POST}; the {@code GET} pages are all read-only, which is a property worth keeping for its
 * own sake, because a {@code GET} that changes data can be triggered by an image tag on any web
 * page in the world.</p>
 *
 * <p>A missing or wrong token produces {@code 403} and a plain page rather than a redirect to the
 * form. A redirect would be friendlier for the rare legitimate case - a form left open until the
 * session was replaced - but it would also silently hide a genuine forgery attempt, and this is
 * exactly the event that should be visible in the log.</p>
 */
public class CsrfFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(CsrfFilter.class.getName());

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (!"POST".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        if (Csrf.isValid(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        LOGGER.log(Level.WARNING, "Rejected a POST to {0} with a missing or invalid CSRF token",
                httpRequest.getRequestURI());

        httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);

        // The JSON channel gets a JSON refusal. A caller expecting to parse a response must not be
        // handed a page of HTML - it would fail on the parse and report the wrong cause.
        String path = httpRequest.getRequestURI()
                .substring(httpRequest.getContextPath().length());

        if (path.startsWith("/api/")) {
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.getWriter().print(lk.sunrisedental.util.JsonWriter.object()
                    .add("error", "CSRF_TOKEN_INVALID")
                    .add("message", "This request did not carry a valid token for your session. "
                            + "Fetch one from GET /api/clinic/session and send it as csrfToken.")
                    .toJson());
            return;
        }

        httpResponse.setContentType("text/html; charset=UTF-8");
        httpResponse.getWriter().print(
                "<!doctype html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<title>Request refused</title><link rel=\"stylesheet\" href=\""
                + httpRequest.getContextPath() + "/assets/css/main.css\"></head>"
                + "<body class=\"centred\"><main class=\"panel\">"
                + "<h1>Request refused</h1>"
                + "<p class=\"notice\">This form could not be accepted because your session has "
                + "changed since it was opened. Please go back, reload the page and try again.</p>"
                + "<p><a class=\"button primary\" href=\"" + httpRequest.getContextPath()
                + "/app/dashboard\">Return to the dashboard</a></p></main></body></html>");
    }
}