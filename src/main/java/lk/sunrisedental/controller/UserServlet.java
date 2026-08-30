package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.model.Role;
import lk.sunrisedental.model.User;

import java.io.IOException;
import java.util.Arrays;

/**
 * Staff account administration: {@code /app/users/*}. Administrators only.
 *
 * <p>Three separate mechanisms keep this screen to administrators, and each covers a gap the
 * others leave. The navigation menu does not show the link to anyone else; the authorisation
 * filter refuses the URL to anyone else, which is what happens when a receptionist follows an old
 * bookmark; and the facade refuses each operation, which is what happens if this servlet is ever
 * reached by a route nobody anticipated. All three ask {@code Role.canManageMasterData()}, so they
 * cannot disagree about who is an administrator.</p>
 *
 * <p><strong>There is no delete.</strong> Every appointment records who booked it, and the
 * foreign key is {@code ON DELETE RESTRICT}, so removing an account would either be refused by the
 * database or destroy the record of who did what. Deactivation is the only removal offered:
 * {@code AuthenticationService} refuses a deactivated account at sign-in, so the effect is
 * immediate, while everything that person ever booked stays attributable.</p>
 *
 * <p>Passwords are read into a {@code char[]} and wiped in a {@code finally} block. They are never
 * echoed back into a redisplayed form - a rejected account form keeps the username and the name,
 * and the administrator retypes the password.</p>
 */
@WebServlet(name = "UserServlet", urlPatterns = {"/app/users", "/app/users/*"})
public class UserServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        switch (action(request)) {
            case "add" -> showAddForm(request, response);
            case "edit" -> showEditForm(request, response);
            case "password" -> showPasswordForm(request, response);
            case "" -> showList(request, response);
            default -> throw badRequest("There is no staff-account page at that address.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        switch (action(request)) {
            case "add" -> add(request, response);
            case "edit" -> update(request, response);
            case "activate" -> setActive(request, response);
            case "password" -> resetPassword(request, response);
            default -> throw badRequest("That staff-account action is not recognised.");
        }
    }

    // ------------------------------------------------------------------ list

    private void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setAttribute("staff", facade().staff(currentUser(request)));
        request.setAttribute("roles", Role.assignableByAdministrator());

        render(request, response, "users/list", "Staff accounts", "/app/users");
    }

    // ------------------------------------------------------------------ create

    private void showAddForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setAttribute("roles", Role.assignableByAdministrator());
        render(request, response, "users/add", "Add a staff account", "/app/users");
    }

    private void add(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = param(request, "username");
        String fullName = param(request, "fullName");
        Role role = Role.fromString(param(request, "role")).orElse(null);
        char[] password = toCharArray(request.getParameter("password"));

        try {
            User created = facade().addStaff(currentUser(request), username, fullName, role, password);

            flash(request, true, "The account for " + created.getFullName()
                    + " has been created. They can sign in as " + created.getUsername() + ".");
            redirect(request, response, "/app/users");

        } catch (ValidationException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("validation", e.getValidationResult());
            request.setAttribute("username", username);
            request.setAttribute("fullName", fullName);
            request.setAttribute("selectedRole", role);
            request.setAttribute("roles", Role.assignableByAdministrator());

            render(request, response, "users/add", "Add a staff account", "/app/users");

        } finally {
            wipe(password);
        }
    }

    // ------------------------------------------------------------------ amend

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setAttribute("staffMember", facade().findStaff(currentUser(request), requiredId(request)));
        request.setAttribute("roles", Role.assignableByAdministrator());

        render(request, response, "users/edit", "Amend staff account", "/app/users");
    }

    private void update(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        int userId = requiredId(request);
        String fullName = param(request, "fullName");
        Role role = Role.fromString(param(request, "role")).orElse(null);

        try {
            User saved = facade().updateStaff(actor, userId, fullName, role);

            flash(request, true, "The account for " + saved.getFullName() + " has been updated.");
            redirect(request, response, "/app/users");

        } catch (ValidationException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("validation", e.getValidationResult());
            request.setAttribute("staffMember", facade().findStaff(actor, userId));
            request.setAttribute("roles", Role.assignableByAdministrator());

            render(request, response, "users/edit", "Amend staff account", "/app/users");

        } catch (BusinessRuleException e) {
            // Demoting the last administrator. The system must always keep a way back in.
            flash(request, false, e.getUserMessage());
            redirect(request, response, "/app/users/edit?id=" + userId);
        }
    }

    // ------------------------------------------------------------------ activate and deactivate

    /**
     * Deactivates or reactivates an account.
     *
     * <p>The business tier refuses two cases outright: deactivating yourself, and deactivating the
     * last active administrator. Either would leave the clinic locked out of its own user
     * management, recoverable only by editing the database by hand.</p>
     */
    private void setActive(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        int userId = requiredId(request);
        boolean active = Boolean.parseBoolean(param(request, "active"));

        try {
            User saved = facade().setStaffActive(currentUser(request), userId, active);

            flash(request, true, saved.getFullName() + (active
                    ? " can sign in again."
                    : " has been deactivated and can no longer sign in."));

        } catch (BusinessRuleException e) {
            flash(request, false, e.getUserMessage());
        }

        redirect(request, response, "/app/users");
    }

    // ------------------------------------------------------------------ password reset

    private void showPasswordForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setAttribute("staffMember", facade().findStaff(currentUser(request), requiredId(request)));
        render(request, response, "users/password", "Reset password", "/app/users");
    }

    private void resetPassword(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        int userId = requiredId(request);
        char[] password = toCharArray(request.getParameter("password"));

        try {
            User saved = facade().resetStaffPassword(actor, userId, password);

            flash(request, true, "The password for " + saved.getFullName() + " has been changed.");
            redirect(request, response, "/app/users");

        } catch (ValidationException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("validation", e.getValidationResult());
            request.setAttribute("staffMember", facade().findStaff(actor, userId));

            render(request, response, "users/password", "Reset password", "/app/users");

        } finally {
            wipe(password);
        }
    }

    private static int requiredId(HttpServletRequest request) {
        Integer userId = optionalIntParam(request, "id");
        if (userId == null) {
            throw new BusinessRuleException("BAD_REQUEST", "Please give a staff account.");
        }
        return userId;
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
