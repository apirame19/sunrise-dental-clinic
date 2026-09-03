package lk.sunrisedental.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lk.sunrisedental.model.User;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ends a session: {@code POST /logout}.
 *
 * <p><strong>{@code POST}, not {@code GET}.</strong> A sign-out link that works as a {@code GET}
 * can be triggered by an image tag on any page in the world, which is a nuisance attack rather than
 * a dangerous one - but it is also the reason the sign-out control in the layout is a one-button
 * form carrying the CSRF token, like every other state-changing action here. Keeping the rule
 * uniform is what stops somebody reasoning about which exceptions are safe.</p>
 *
 * <p>The session is invalidated outright rather than having the user attribute removed. Removing
 * the attribute would leave a live session id in the browser, holding whatever else had
 * accumulated in it; invalidation is what makes the cookie the user walks away with worthless.
 * On a shared front-desk machine that is the whole point of the button.</p>
 *
 * <p>This servlet extends {@link HttpServlet} directly. It renders nothing, needs no facade and
 * must work even if the business tier is unreachable - being unable to sign out because the
 * database is down would be an unpleasant surprise.</p>
 */
@WebServlet(name = "LogoutServlet", urlPatterns = "/logout")
public class LogoutServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(LogoutServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);

        if (session != null) {
            Object user = session.getAttribute(SessionKeys.USER);
            if (user instanceof User signedOut) {
                LOGGER.log(Level.INFO, "{0} signed out", signedOut.getUsername());
            }
            session.invalidate();
        }

        response.sendRedirect(request.getContextPath() + "/login?reason=signedout");
    }

    /**
     * A {@code GET} to this URL is somebody typing it or following a stale bookmark, not a
     * sign-out. They are sent to the login page, which will show them their session is intact if
     * it is.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendRedirect(request.getContextPath() + "/login");
    }
}
