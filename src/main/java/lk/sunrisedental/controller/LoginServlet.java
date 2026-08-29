package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lk.sunrisedental.controller.filter.AuthorizationFilter;
import lk.sunrisedental.exception.AuthenticationException;
import lk.sunrisedental.model.Role;
import lk.sunrisedental.model.User;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The staff sign-in screen: {@code GET /login} shows it, {@code POST /login} attempts it.
 *
 * <p><strong>The session is replaced, not reused.</strong> On a successful sign-in the existing
 * session is invalidated and a new one created, so the browser is issued a new session id. Without
 * this, an attacker who can set a victim's session cookie before they sign in - by any of the
 * usual routes - ends up holding a session that is now authenticated as that victim. Session
 * fixation is cheap to prevent here and impossible to fix later.</p>
 *
 * <p>The password is read into a {@code char[]} and wiped in a {@code finally} block. This is
 * partly theatre in a servlet container - {@code request.getParameter} has already produced a
 * {@code String}, and that String is out of reach - but the habit is what keeps the credential out
 * of the parts of the code this class controls, and the business tier below it never sees a
 * String password at all.</p>
 *
 * <p>Every failure produces the same sentence. Distinguishing "no such user" from "wrong password"
 * would turn this form into a way of confirming which staff usernames exist; the one exception is
 * a locked account, where saying so is the only way the user can understand why waiting is the
 * answer.</p>
 */
@WebServlet(name = "LoginServlet", urlPatterns = "/login")
public class LoginServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(LoginServlet.class.getName());

    /** Where a member of staff with no remembered destination lands. */
    private static final String DEFAULT_DESTINATION = "/app/dashboard";

    /** Where a patient lands. The staff dashboard is refused to them by the authorisation filter. */
    private static final String PATIENT_DESTINATION = "/app/my/appointments";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Already signed in: there is nothing to do here, and showing the form would invite
        // somebody to sign in a second time over the top of an active session.
        User signedIn = currentUser(request);
        if (signedIn != null) {
            redirect(request, response, homeFor(signedIn));
            return;
        }

        // Creating the session here is what gives the form a CSRF token to carry. It is replaced
        // wholesale on success, so nothing an attacker could have planted in it survives.
        request.getSession(true);
        request.setAttribute("notice", noticeFor(param(request, "reason")));

        renderLogin(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = param(request, "username");
        char[] password = toCharArray(request.getParameter("password"));

        try {
            User user = facade().signIn(username, password, clientAddress(request));
            startSessionFor(request, user);

            LOGGER.log(Level.INFO, "{0} signed in as {1}",
                    new Object[]{user.getUsername(), user.getRole()});

            redirect(request, response, destinationFor(request, user));

        } catch (AuthenticationException e) {
            // Deliberately not rethrown. BaseServlet would turn this into a 401 error page, and
            // the right response to a bad password is the form again, with the username kept.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            request.setAttribute("errorMessage", e.getUserMessage());
            request.setAttribute("username", username);
            renderLogin(request, response);

        } finally {
            wipe(password);
        }
    }

    /**
     * Replaces the session and puts the user in it.
     *
     * <p>The destination remembered before sign-in has to be carried across the invalidation by
     * hand, because everything in the old session is discarded - which is the entire point of
     * invalidating it.</p>
     */
    private static void startSessionFor(HttpServletRequest request, User user) {
        HttpSession oldSession = request.getSession(false);
        String remembered = oldSession == null
                ? null
                : (String) oldSession.getAttribute(SessionKeys.REDIRECT_AFTER_LOGIN);

        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.USER, user);
        if (remembered != null) {
            session.setAttribute(SessionKeys.REDIRECT_AFTER_LOGIN, remembered);
        }
        // A token minted before authentication must not survive it.
        Csrf.regenerate(session);
    }

    /**
     * @return where to send the user, consuming any remembered destination
     */
    private static String destinationFor(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return homeFor(user);
        }
        Object remembered = session.getAttribute(SessionKeys.REDIRECT_AFTER_LOGIN);
        session.removeAttribute(SessionKeys.REDIRECT_AFTER_LOGIN);

        if (!(remembered instanceof String target) || !isSafeInternalPath(target)) {
            return homeFor(user);
        }
        // A dentist who bookmarked the staff-accounts page must not be dropped on a 403 the
        // instant they sign in. The authorisation rules are matched on the path, so the query
        // string is removed before asking.
        int query = target.indexOf('?');
        String path = query < 0 ? target : target.substring(0, query);

        return AuthorizationFilter.isPermitted(path, user.getRole())
                ? target
                : homeFor(user);
    }

    /**
     * @return the landing page for a role.
     *
     *         <p>A patient is sent to their own appointments rather than the staff dashboard,
     *         which the authorisation filter would refuse them - landing a user on a 403 the
     *         instant they sign in reads as a broken application.</p>
     */
    private static String homeFor(User user) {
        return user != null && user.getRole() == Role.PATIENT
                ? PATIENT_DESTINATION
                : DEFAULT_DESTINATION;
    }

    /**
     * @return {@code true} only for a path inside this application.
     *         The value is written by {@code AuthenticationFilter} from the request itself and is
     *         never taken from a parameter, so this is belt and braces - but an open redirect is
     *         exactly the defect that appears when somebody later adds a {@code ?next=} parameter
     *         and assumes the check downstream is doing this.
     */
    private static boolean isSafeInternalPath(String target) {
        return target != null
                && target.startsWith("/")
                && !target.startsWith("//")
                && !target.contains("://")
                && !target.contains("\\");
    }

    private void renderLogin(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        render(request, response, "login", "Staff sign-in", null);
    }

    /** @return the explanation for why the user was sent here, or null if they simply came. */
    private static String noticeFor(String reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case "timeout" -> "Your session has ended after a period of inactivity. "
                    + "Please sign in again.";
            case "required" -> "Please sign in to use the clinic system.";
            case "signedout" -> "You have been signed out.";
            default -> null;
        };
    }

    /**
     * @return the client address, for the sign-in audit trail. {@code X-Forwarded-For} is not
     *         consulted: it is trivially forged, and this application is not deployed behind a
     *         proxy that would set it.
     */
    private static String clientAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private static char[] toCharArray(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }

    private static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
