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
import lk.sunrisedental.model.Role;
import lk.sunrisedental.model.User;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Refuses a signed-in user access to a URL their role may not use.
 *
 * <p>This runs after {@link AuthenticationFilter}, so by the time it is reached the question is no
 * longer "who are you" but "may you". The two are separate filters because they fail differently:
 * not being signed in is fixed by signing in, and is therefore a redirect; not being permitted is
 * not fixed by anything the user can do, and is therefore a {@code 403}. Collapsing them would send
 * a receptionist who clicked a bookmarked admin URL to a login page they are already past, which
 * reads as a broken application.</p>
 *
 * <p><strong>This is deliberately the second of three checks, not the only one.</strong>
 * {@code Navigation} hides links a role may not use, this filter refuses the URLs behind them, and
 * {@code ClinicManagementFacade} refuses the operation itself. All three ask the same
 * {@link Role} predicates, so they cannot disagree - but each covers a gap the others leave.
 * Hiding a link does not stop anyone typing the URL. A filter matches on paths, so a servlet added
 * at a path nobody thought to list here would slip through, which is why the facade checks again at
 * the point the work is actually done. The facade check is the guarantee; this one exists so the
 * refusal arrives as a page rather than as a half-rendered screen, and so that an unauthorised
 * attempt is logged in one place.</p>
 *
 * <p>The rules are ordered most-specific-first and the first matching prefix wins, so
 * {@code /app/dentists/add} can be administrator-only while {@code /app/dentists} stays open to
 * everyone. A path that matches no rule is permitted: the paths listed here are the restricted
 * ones, and the alternative - denying by default - would mean every new read-only page had to be
 * registered here before it worked at all.</p>
 */
public class AuthorizationFilter implements Filter {

    private static final Logger LOGGER = Logger.getLogger(AuthorizationFilter.class.getName());

    /**
     * One path prefix and the authority needed to reach it.
     *
     * @param prefix    the context-relative path prefix, matched with {@code startsWith}
     * @param permitted the question asked of the signed-in user's role
     * @param action    what the user was trying to do, for the refusal page and the log
     */
    private record Rule(String prefix, Predicate<Role> permitted, String action) {
    }

    /**
     * Most specific first. Every predicate here is a method on {@link Role}, which is the same
     * method the navigation menu and the facade call.
     */
    private static final List<Rule> RULES = List.of(
            // Staff account administration, in both channels.
            new Rule("/app/users", Role::canManageMasterData, "manage staff accounts"),
            new Rule("/api/users", Role::canManageMasterData, "manage staff accounts"),

            // Adding, amending and withdrawing dentists is master data; viewing the roster is not.
            new Rule("/app/dentists/add", Role::canManageMasterData, "add dentists"),
            new Rule("/app/dentists/edit", Role::canManageMasterData, "amend dentist records"),
            new Rule("/app/dentists/activate", Role::canManageMasterData,
                    "withdraw or reinstate dentists"),

            // Billing.
            new Rule("/app/billing", Role::canGenerateBills, "use billing"),
            new Rule("/api/bills", Role::canGenerateBills, "use billing"),

            // Registration of patients and appointments.
            new Rule("/app/appointments/register", Role::canRegisterAppointments,
                    "register appointments"),
            new Rule("/app/patients/register", Role::canRegisterAppointments,
                    "register patients"),
            new Rule("/app/patients/edit", Role::canRegisterAppointments,
                    "amend patient records"));

    /**
     * The only paths a {@link Role#PATIENT} may reach.
     *
     * <p><strong>Patients are allow-listed; staff are deny-listed.</strong> The {@code RULES} table
     * above names the restricted paths and permits everything else, which is right for staff: a new
     * read-only staff page works without being registered here first. Applying that default to a
     * patient would be a disaster - it would hand them {@code /app/patients}, which is every
     * patient's name, address and telephone number, plus the whole appointment book. So this one
     * role is inverted: anything not named here is refused.</p>
     *
     * <p>Because the default is refusal, a page added later is invisible to patients until somebody
     * deliberately adds it. That is the correct way round for the role that belongs to the
     * public.</p>
     */
    private static final List<String> PATIENT_ALLOWED_PREFIXES = List.of(
            "/app/my",
            "/app/help");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        User user = signedInUser(httpRequest);
        if (user == null || user.getRole() == null) {
            // AuthenticationFilter owns this case. Reaching here means the path is outside its
            // mapping, and letting the request through would be the mistake.
            chain.doFilter(request, response);
            return;
        }

        String path = pathWithinApplication(httpRequest);
        Rule broken = firstBrokenRule(path, user.getRole());

        if (broken == null) {
            chain.doFilter(request, response);
            return;
        }

        LOGGER.log(Level.INFO, "Refused {0} ({1}) access to {2}",
                new Object[]{user.getUsername(), user.getRole(), path});

        refuse(httpRequest, httpResponse, broken.action());
    }

    /**
     * @param path the context-relative path
     * @param role the signed-in user's role
     * @return the first rule this request fails, or null if it breaks none
     */
    static Rule firstBrokenRule(String path, Role role) {
        // Patients are refused by default and permitted only where explicitly listed. See
        // PATIENT_ALLOWED_PREFIXES for why this one role is inverted.
        if (role == Role.PATIENT) {
            return isPatientArea(path)
                    ? null
                    : new Rule(path, r -> false, "use the clinic's staff pages");
        }

        // Staff pages that belong to a patient are refused to staff here only in the sense that
        // there is nothing for them: /app/my resolves the record from the session, so a member of
        // staff would see "no patient record linked to this account".
        for (Rule rule : RULES) {
            if (path.equals(rule.prefix()) || path.startsWith(rule.prefix() + "/")) {
                return rule.permitted().test(role) ? null : rule;
            }
        }
        return null;
    }

    /** @return {@code true} if the path is one of the patient's own pages. */
    private static boolean isPatientArea(String path) {
        for (String prefix : PATIENT_ALLOWED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param path the context-relative path
     * @param role the role to test
     * @return {@code true} if that role may reach that path. Exposed for the unit tests, which
     *         assert the table above matches the navigation menu.
     */
    public static boolean isPermitted(String path, Role role) {
        return firstBrokenRule(path, role) == null;
    }

    private static User signedInUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object user = session.getAttribute(SessionKeys.USER);
        return user instanceof User signedIn ? signedIn : null;
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /**
     * Sends the refusal.
     *
     * <p>A JSON request gets a JSON envelope and an HTML request gets a page, because the API
     * channel is consumed by something that will try to parse whatever comes back. The two share
     * this one method so the status code and the wording cannot diverge.</p>
     */
    private static void refuse(HttpServletRequest request, HttpServletResponse response,
                               String action) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        String message = "You do not have permission to " + action + ".";

        if (pathWithinApplication(request).startsWith("/api/")) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().print(lk.sunrisedental.util.JsonWriter.object()
                    .add("error", "ACCESS_DENIED")
                    .add("message", message)
                    .toJson());
            return;
        }

        response.setContentType("text/html; charset=UTF-8");
        response.getWriter().print(
                "<!doctype html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<title>Not permitted</title>"
                + "<link rel=\"stylesheet\" href=\"" + request.getContextPath()
                + "/assets/css/main.css\"></head><body><main class=\"panel\">"
                + "<h1>Not permitted</h1><p class=\"notice\">"
                + lk.sunrisedental.util.Html.escape(message)
                + "</p><p><a href=\"" + request.getContextPath() + "/app/dashboard\">"
                + "Return to the dashboard</a></p></main></body></html>");
    }
}
