package lk.sunrisedental.controller.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lk.sunrisedental.controller.SessionKeys;
import lk.sunrisedental.model.User;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Refuses every page under {@code /app/*} to anyone who is not signed in.
 *
 * <p><strong>Why a filter rather than a check in each servlet.</strong> A per-servlet check
 * protects the servlets somebody remembered to write it into. A filter mapped to {@code /app/*}
 * protects everything at that path, including the servlet added next month by someone who has not
 * read this comment. Access control that depends on being remembered is not access control.</p>
 *
 * <p>Two refusals are distinguished, because they mean different things to the user:</p>
 *
 * <ul>
 *   <li><strong>Never signed in</strong> - sent to the login page, with the page they wanted
 *       remembered so they land on it afterwards rather than on a generic dashboard.</li>
 *   <li><strong>Session expired</strong> - the browser presented a session id the container no
 *       longer knows. They are told so explicitly, because "please sign in" after thirty minutes of
 *       work reads like a fault rather than the timeout it is.</li>
 * </ul>
 *
 * <p>Only the target of a {@code GET} is remembered. Replaying a {@code POST} after a login the
 * user has since thought better of would silently re-run a booking or a cancellation.</p>
 */
public class AuthenticationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (isSignedIn(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        boolean expired = httpRequest.getRequestedSessionId() != null
                && !httpRequest.isRequestedSessionIdValid();

        if ("GET".equalsIgnoreCase(httpRequest.getMethod())) {
            rememberTarget(httpRequest);
        }

        httpResponse.sendRedirect(httpRequest.getContextPath()
                + "/login" + (expired ? "?reason=timeout" : "?reason=required"));
    }

    private static boolean isSignedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(SessionKeys.USER) instanceof User;
    }

    /**
     * Stores where the user was heading, so sign-in returns them to it.
     *
     * <p>The value kept is the path and query string built from this request, never a URL supplied
     * by the caller. Redirecting to a caller-supplied address is an open-redirect: an attacker
     * sends a staff member a link to this application's own login page that bounces them to a
     * convincing copy of it afterwards.</p>
     */
    private static void rememberTarget(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String query = request.getQueryString();
        String target = query == null ? path : path + "?" + query;

        request.getSession(true).setAttribute(SessionKeys.REDIRECT_AFTER_LOGIN, target);
    }

    /** @return the target encoded for use in a query string; kept for callers that need it. */
    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
