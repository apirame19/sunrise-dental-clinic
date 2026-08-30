package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.model.User;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Patient self-registration: {@code GET /register/patient} shows the form,
 * {@code POST /register/patient} creates the account.
 *
 * <p>This servlet is outside {@code /app/*}, so the authentication filter does not guard it - which
 * is the point, since the person filling it in has no account yet. The CSRF filter is mapped to it
 * explicitly in {@code web.xml}, so the form still carries a token.</p>
 *
 * <p><strong>No role parameter is read here.</strong> The form has no role field and this servlet
 * never looks for one. {@code facade().registerPatientAccount(...)} takes no role argument at all,
 * and the service assigns {@code Role.PATIENT} as a constant. Appending {@code &role=ADMIN} to a
 * submission therefore has no effect whatsoever - there is no code path that would read it.</p>
 *
 * <p>The password is read into a {@code char[]} and wiped in a {@code finally} block, and is never
 * echoed back into a redisplayed form: a rejected registration keeps the typed details so nothing
 * is lost, and the visitor retypes the password.</p>
 */
@WebServlet(name = "PatientRegistrationServlet", urlPatterns = "/register/patient")
public class PatientRegistrationServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER =
            Logger.getLogger(PatientRegistrationServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Already signed in: registering a second account over an active session is never intended.
        if (currentUser(request) != null) {
            redirect(request, response, "/app/dashboard");
            return;
        }

        // Creates the session that carries the CSRF token into the form.
        request.getSession(true);
        renderForm(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = param(request, "username");
        String fullName = param(request, "fullName");
        String address = param(request, "address");
        String contactNumber = param(request, "contactNumber");
        String email = param(request, "email");
        char[] password = toCharArray(request.getParameter("password"));

        try {
            User created = facade().registerPatientAccount(
                    username, fullName, address, contactNumber, email, password);

            LOGGER.log(Level.INFO, "Patient account registered: {0}", created.getUsername());

            flash(request, true, "Your account has been created, " + created.getFullName()
                    + ". Please sign in with the username " + created.getUsername() + ".");

            redirect(request, response, "/login");

        } catch (ValidationException e) {
            // Every bad field at once, each beside its own input, with the typing kept.
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("validation", e.getValidationResult());
            request.setAttribute("username", username);
            request.setAttribute("fullName", fullName);
            request.setAttribute("address", address);
            request.setAttribute("contactNumber", contactNumber);
            request.setAttribute("email", email);

            renderForm(request, response);

        } finally {
            wipe(password);
        }
    }

    private void renderForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        render(request, response, "register-patient", "Register as a patient", null);
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
