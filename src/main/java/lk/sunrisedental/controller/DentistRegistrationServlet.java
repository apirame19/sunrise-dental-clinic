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
 * Dentist self-registration: {@code GET /register/dentist} shows the form,
 * {@code POST /register/dentist} creates the account.
 *
 * <p>Like the patient form this sits outside {@code /app/*} so an applicant with no account can
 * reach it, and the CSRF filter is mapped to it explicitly in {@code web.xml}.</p>
 *
 * <p><strong>The account is created inactive and cannot sign in yet.</strong> A dentist login can
 * read every patient record and the whole appointment book, so an anonymous applicant does not get
 * to grant themselves that access. An administrator activates the account from the existing
 * staff-accounts screen, and until then {@code AuthenticationService}'s existing "inactive accounts
 * cannot sign in" rule refuses it. The message shown on success says so plainly, so an applicant
 * who then fails to sign in understands why rather than assuming the form was broken.</p>
 *
 * <p><strong>No role parameter is read here.</strong> The form has no role field, this servlet
 * never looks for one, and {@code facade().registerDentistAccount(...)} takes no role argument -
 * the service assigns {@code Role.DENTIST} as a constant.</p>
 */
@WebServlet(name = "DentistRegistrationServlet", urlPatterns = "/register/dentist")
public class DentistRegistrationServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER =
            Logger.getLogger(DentistRegistrationServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (currentUser(request) != null) {
            redirect(request, response, "/app/dashboard");
            return;
        }

        request.getSession(true);
        renderForm(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = param(request, "username");
        String fullName = param(request, "fullName");
        String specialization = param(request, "specialization");
        String licenseNo = param(request, "licenseNo");
        String contactNumber = param(request, "contactNumber");
        char[] password = toCharArray(request.getParameter("password"));

        try {
            User created = facade().registerDentistAccount(
                    username, fullName, specialization, licenseNo, contactNumber, password);

            LOGGER.log(Level.INFO, "Dentist account registered, awaiting approval: {0}",
                    created.getUsername());

            flash(request, true, "Thank you, " + created.getFullName()
                    + ". Your registration has been received. An administrator must approve your "
                    + "account before you can sign in.");

            redirect(request, response, "/login");

        } catch (ValidationException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("validation", e.getValidationResult());
            request.setAttribute("username", username);
            request.setAttribute("fullName", fullName);
            request.setAttribute("specialization", specialization);
            request.setAttribute("licenseNo", licenseNo);
            request.setAttribute("contactNumber", contactNumber);

            renderForm(request, response);

        } finally {
            wipe(password);
        }
    }

    private void renderForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        render(request, response, "register-dentist", "Register as a dentist", null);
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
