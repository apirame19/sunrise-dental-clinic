package lk.sunrisedental.controller.api;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lk.sunrisedental.controller.SessionKeys;
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
import lk.sunrisedental.util.JsonWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared behaviour for the {@code /api/*} JSON endpoints.
 *
 * <p><strong>The JSON channel and the HTML channel are two presentations of one business tier.</strong>
 * Every endpoint below calls the same {@link ClinicManagementFacade} the servlets call, so an
 * authorisation rule, a validation rule or a billing calculation exists once. The alternative -
 * an API that reimplements the rules "just for the integration" - is how one channel ends up
 * missing a check that the other has.</p>
 *
 * <p>Authentication is checked here rather than by the authentication filter, and the difference
 * matters. The filter answers a signed-out browser with a redirect to the login page, which is
 * right for a person and useless to a program: an HTTP client asking for JSON would receive
 * {@code 302} and then a page of HTML. This returns {@code 401} with a JSON body instead. The
 * authorisation filter still applies to {@code /api/*}, because a refusal there is the same
 * refusal in both channels.</p>
 *
 * <p>Every failure becomes {@code {"error": CODE, "message": ...}} with an appropriate status.
 * The codes are the stable {@code errorCode} values from {@link ClinicException}, not the message
 * text, so a caller can branch on them while the wording is still free to improve.</p>
 */
public abstract class ApiServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(ApiServlet.class.getName());

    protected static final String JSON = "application/json";

    /** @return the single entry point into the business tier. */
    protected ClinicManagementFacade facade() {
        return ServiceRegistry.getInstance().getFacade();
    }

    /**
     * Runs the request and converts any business failure into a JSON envelope.
     *
     * <p>{@link ValidationException} is handled here, unlike in the HTML controllers. There is no
     * form to repopulate, so the correct answer is {@code 422} carrying the field-error map - which
     * is the same map the JSP prints beside its inputs, produced by the same validation chain.</p>
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType(JSON);
        response.setCharacterEncoding("UTF-8");

        try {
            if (!requireSignedIn(request, response)) {
                return;
            }
            super.service(request, response);

        } catch (ValidationException e) {
            JsonWriter.JsonObject errors = JsonWriter.object();
            e.getValidationResult().getFieldErrors().forEach(errors::add);

            JsonWriter.JsonArray global = JsonWriter.array();
            e.getValidationResult().getGlobalErrors().forEach(global::add);

            write(response, 422, JsonWriter.object()
                    .add("error", e.getErrorCode())
                    .add("message", e.getUserMessage())
                    .add("fieldErrors", errors)
                    .add("globalErrors", global));

        } catch (AccessDeniedException e) {
            fail(response, HttpServletResponse.SC_FORBIDDEN, e);

        } catch (AppointmentNotFoundException e) {
            fail(response, HttpServletResponse.SC_NOT_FOUND, e);

        } catch (AuthenticationException e) {
            fail(response, HttpServletResponse.SC_UNAUTHORIZED, e);

        } catch (DataAccessException e) {
            // The detailed message names tables and columns; it belongs in the log, not on the wire.
            LOGGER.log(Level.SEVERE, "Data access failure serving " + request.getRequestURI(), e);
            fail(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);

        } catch (ClinicException e) {
            fail(response, HttpServletResponse.SC_BAD_REQUEST, e);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected failure serving " + request.getRequestURI(), e);
            write(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, JsonWriter.object()
                    .add("error", "INTERNAL_ERROR")
                    .add("message", "The request could not be completed."));
        }
    }

    /**
     * @return {@code true} if the caller is signed in; otherwise writes {@code 401} and returns
     *         {@code false}
     */
    private boolean requireSignedIn(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        if (isPublic(request)) {
            return true;
        }
        if (currentUser(request) != null) {
            return true;
        }
        write(response, HttpServletResponse.SC_UNAUTHORIZED, JsonWriter.object()
                .add("error", "NOT_AUTHENTICATED")
                .add("message", "Sign in to the clinic system before calling this endpoint."));
        return false;
    }

    /**
     * @return {@code true} if this endpoint may be called without signing in. Only the liveness
     *         probe overrides this, and it exposes no clinic data.
     */
    protected boolean isPublic(HttpServletRequest request) {
        return false;
    }

    /** @return the signed-in user, or null. */
    protected User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object user = session.getAttribute(SessionKeys.USER);
        return user instanceof User signedIn ? signedIn : null;
    }

    // ------------------------------------------------------------------ writing

    /**
     * Writes a JSON body and status.
     *
     * @param response the response
     * @param status   the HTTP status code
     * @param body     the object or array to render
     */
    protected static void write(HttpServletResponse response, int status, JsonWriter.JsonValue body)
            throws IOException {

        if (response.isCommitted()) {
            LOGGER.warning("Attempted to write a JSON body after the response was committed");
            return;
        }
        response.setStatus(status);
        try (PrintWriter out = response.getWriter()) {
            out.print(body.toJson());
        }
    }

    /** Writes {@code 200} and the given body. */
    protected static void ok(HttpServletResponse response, JsonWriter.JsonValue body)
            throws IOException {
        write(response, HttpServletResponse.SC_OK, body);
    }

    private static void fail(HttpServletResponse response, int status, ClinicException e)
            throws IOException {
        write(response, status, JsonWriter.object()
                .add("error", e.getErrorCode())
                .add("message", e.getUserMessage()));
    }

    // ------------------------------------------------------------------ parameters

    /** @return the trimmed parameter, or null if absent or blank. */
    protected static String param(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * @return the parsed date
     * @throws BusinessRuleException if the value is present but not an ISO date. Unlike the HTML
     *         controllers, which quietly fall back to today, an API caller is told they sent
     *         something wrong - silently substituting a different date would make a program's
     *         output inexplicable.
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
            throw new BusinessRuleException("BAD_DATE",
                    "'" + value + "' is not a date. Use the form YYYY-MM-DD.");
        }
    }

    /** @return the parsed number, or null if absent; refuses a malformed one. */
    protected static Integer optionalIntParam(HttpServletRequest request, String name) {
        String value = param(request, name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("BAD_NUMBER",
                    "'" + value + "' is not a whole number.");
        }
    }

    /**
     * @return the path after the servlet mapping without its leading slash, never null.
     *         {@code /api/appointments/search} under {@code /api/appointments/*} yields
     *         {@code "search"}.
     */
    protected static String action(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            return "";
        }
        String action = pathInfo.substring(1);
        return action.endsWith("/") ? action.substring(0, action.length() - 1) : action;
    }
}
