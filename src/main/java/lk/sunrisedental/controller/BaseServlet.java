package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lk.sunrisedental.exception.AccessDeniedException;
import lk.sunrisedental.exception.AppointmentNotFoundException;
import lk.sunrisedental.exception.AuthenticationException;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.exception.ClinicException;
import lk.sunrisedental.exception.DataAccessException;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.facade.ClinicManagementFacade;
import lk.sunrisedental.facade.ServiceRegistry;
import lk.sunrisedental.model.User;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared behaviour for every page-rendering servlet.
 *
 * <p>Four things are centralised here, each because doing it once per servlet is how they end up
 * being done differently:</p>
 *
 * <ul>
 *   <li><strong>One dependency.</strong> {@link #facade()} is the only business-tier reference a
 *       controller has. No servlet in this application imports a service, a DAO or
 *       {@code java.sql}.</li>
 *   <li><strong>Consistent page furniture.</strong> {@link #render} attaches the role-filtered
 *       navigation, the clinic settings and the CSRF token to every page, so no view can be
 *       rendered with a menu that does not match the viewer's role.</li>
 *   <li><strong>Uniform failure handling.</strong> {@link #service} catches every
 *       {@link ClinicException} and turns it into the right status code and a readable page.
 *       Unexpected faults produce a fixed sentence, never a stack trace - a Java exception message
 *       on screen is both alarming and an information disclosure.</li>
 *   <li><strong>Parameter parsing.</strong> Dates and numbers arrive as text and are frequently
 *       absent or malformed; the helpers below fall back rather than throwing, because a bad query
 *       string should show today's list, not an error page.</li>
 * </ul>
 */
public abstract class BaseServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /** Where the views live. Under {@code WEB-INF} so no JSP can be requested directly. */
    private static final String VIEW_ROOT = "/WEB-INF/views/";

    private static final Logger LOGGER = Logger.getLogger(BaseServlet.class.getName());

    /** @return the single entry point into the business tier. */
    protected ClinicManagementFacade facade() {
        return ServiceRegistry.getInstance().getFacade();
    }

    /**
     * @param request the current request
     * @return the signed-in user, or null. Pages under {@code /app/*} are behind the authentication
     *         filter, so in practice this is never null there.
     */
    protected User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object user = session.getAttribute(SessionKeys.USER);
        return user instanceof User signedIn ? signedIn : null;
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Forwards to a view, attaching the furniture every page needs.
     *
     * @param request   the current request
     * @param response  the response
     * @param viewName  the view, relative to {@code /WEB-INF/views} and without the extension
     * @param title     the page heading
     * @param activeNav the navigation url to mark as current, or null
     */
    protected void render(HttpServletRequest request, HttpServletResponse response,
                          String viewName, String title, String activeNav)
            throws ServletException, IOException {

        User user = currentUser(request);
        if (user != null) {
            request.setAttribute(SessionKeys.NAV, Navigation.forRole(user.getRole()));
        }
        request.setAttribute(SessionKeys.PAGE_TITLE, title);
        request.setAttribute(SessionKeys.ACTIVE_NAV, activeNav);
        request.setAttribute(SessionKeys.CLINIC, facade().configuration());

        HttpSession session = request.getSession(false);
        if (session != null) {
            request.setAttribute(Csrf.FIELD, Csrf.token(session));
            moveFlashToRequest(request, session);
        }

        request.getRequestDispatcher(VIEW_ROOT + viewName + ".jsp").forward(request, response);
    }

    /**
     * Moves one-shot messages from the session into request scope and clears them.
     *
     * <p>Flash messages exist so that a POST can redirect - which is what stops a browser refresh
     * re-submitting a booking - and still tell the user what happened. Clearing them here is what
     * makes them one-shot; leaving them would show "Appointment registered" on every subsequent
     * page.</p>
     */
    private static void moveFlashToRequest(HttpServletRequest request, HttpSession session) {
        Object success = session.getAttribute(SessionKeys.FLASH_SUCCESS);
        if (success != null) {
            request.setAttribute(SessionKeys.FLASH_SUCCESS, success);
            session.removeAttribute(SessionKeys.FLASH_SUCCESS);
        }
        Object error = session.getAttribute(SessionKeys.FLASH_ERROR);
        if (error != null) {
            request.setAttribute(SessionKeys.FLASH_ERROR, error);
            session.removeAttribute(SessionKeys.FLASH_ERROR);
        }
    }

    /**
     * Records a message to be shown after a redirect.
     *
     * @param request the current request
     * @param success whether it is good news
     * @param message the sentence to show
     */
    protected void flash(HttpServletRequest request, boolean success, String message) {
        request.getSession(true).setAttribute(
                success ? SessionKeys.FLASH_SUCCESS : SessionKeys.FLASH_ERROR, message);
    }

    /**
     * Redirects to a path within this application.
     *
     * @param path context-relative, for example {@code /app/dashboard}
     */
    protected void redirect(HttpServletRequest request, HttpServletResponse response, String path)
            throws IOException {
        response.sendRedirect(request.getContextPath() + path);
    }

    // ------------------------------------------------------------------ failure handling

    /**
     * Runs the request and converts any business failure into a page.
     *
     * <p>{@link ValidationException} is deliberately <em>not</em> handled here. A rejected form
     * must be redisplayed with the user's input intact and each message beside its own field, which
     * only the servlet that built the form can do; a generic error page would throw away eight
     * fields of typing.</p>
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            super.service(request, response);

        } catch (AccessDeniedException e) {
            LOGGER.log(Level.INFO, "Access refused: {0}", e.getAction());
            fail(request, response, HttpServletResponse.SC_FORBIDDEN,
                    "Not permitted", e.getUserMessage());

        } catch (AppointmentNotFoundException e) {
            fail(request, response, HttpServletResponse.SC_NOT_FOUND,
                    "Not found", e.getUserMessage());

        } catch (AuthenticationException e) {
            fail(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Sign-in required", e.getUserMessage());

        } catch (DataAccessException e) {
            // The detailed message names tables and columns; it belongs in the log, not on screen.
            LOGGER.log(Level.SEVERE, "Data access failure", e);
            fail(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Service unavailable", e.getUserMessage());

        } catch (ClinicException e) {
            fail(request, response, HttpServletResponse.SC_BAD_REQUEST,
                    "That could not be done", e.getUserMessage());
        }
    }

    /**
     * Renders the error page, unless the response has already started.
     *
     * <p>Once bytes are on the wire the status line is fixed and a forward would throw, masking the
     * original failure with an {@code IllegalStateException}. In that case the only honest thing
     * left is to log it.</p>
     */
    private void fail(HttpServletRequest request, HttpServletResponse response,
                      int status, String heading, String message)
            throws ServletException, IOException {

        if (response.isCommitted()) {
            LOGGER.log(Level.WARNING, "Failure after response committed: {0}", message);
            return;
        }
        response.setStatus(status);
        request.setAttribute("errorHeading", heading);
        request.setAttribute("errorMessage", message);
        request.setAttribute("errorStatus", status);
        render(request, response, "error", heading, null);
    }

    // ------------------------------------------------------------------ parameters

    /**
     * @param request the current request
     * @param name    the parameter name
     * @return the trimmed value, or null if absent or blank
     */
    protected static String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * @param request  the current request
     * @param name     the parameter name
     * @param fallback the value to use if absent or not a number
     * @return the parsed number
     */
    protected static int intParam(HttpServletRequest request, String name, int fallback) {
        String value = param(request, name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * @param request the current request
     * @param name    the parameter name
     * @return the parsed number, or null if absent or malformed
     */
    protected static Integer optionalIntParam(HttpServletRequest request, String name) {
        String value = param(request, name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @param request  the current request
     * @param name     the parameter name
     * @param fallback the date to use if absent or malformed
     * @return the parsed ISO date
     */
    protected static LocalDate dateParam(HttpServletRequest request, String name,
                                         LocalDate fallback) {
        String value = param(request, name);
        if (value == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    /**
     * @param request the current request
     * @return the path after the servlet mapping, without its leading slash, never null.
     *         {@code /app/patients/view} under a {@code /app/patients/*} mapping yields
     *         {@code "view"}.
     */
    protected static String action(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            return "";
        }
        String action = pathInfo.substring(1);
        return action.endsWith("/") ? action.substring(0, action.length() - 1) : action;
    }

    /**
     * @param message the sentence to show
     * @throws BusinessRuleException always; a convenience for rejecting a malformed request
     */
    protected static BusinessRuleException badRequest(String message) {
        return new BusinessRuleException("BAD_REQUEST", message);
    }

    /**
     * Escapes a value for use in the query string of a redirect.
     *
     * <p>Needed because these redirects carry appointment and bill numbers. Those are generated
     * with a safe alphabet today, but a redirect that only works while its data happens to contain
     * no reserved characters is a defect waiting for the first patient whose reference contains a
     * space.</p>
     *
     * @param value the raw value; null becomes the empty string
     * @return the value, percent-encoded
     */
    protected static String encode(String value) {
        return value == null
                ? ""
                : java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
