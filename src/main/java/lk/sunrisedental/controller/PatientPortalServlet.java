package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.model.PatientHistory;

import java.io.IOException;

/**
 * The patient's own pages: {@code /app/my/*}.
 *
 * <p>This is the only area of {@code /app/*} a {@code PATIENT} may reach; the authorisation filter
 * refuses that role everything else. Staff never come here - their equivalent is
 * {@code /app/patients/view}, which can show any patient and is restricted accordingly.</p>
 *
 * <p><strong>The patient is identified by the session, never by a parameter.</strong> There is no
 * {@code ?id=} on this page, and {@code facade().myHistory(actor)} resolves the record from the
 * signed-in account. A patient therefore has no URL to edit in order to read somebody else's
 * history - the capability simply is not expressed anywhere in this servlet.</p>
 */
@WebServlet(name = "PatientPortalServlet", urlPatterns = {"/app/my", "/app/my/*"})
public class PatientPortalServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        switch (action(request)) {
            case "", "appointments" -> showMyAppointments(request, response);
            default -> throw badRequest("There is no page at that address.");
        }
    }

    private void showMyAppointments(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        PatientHistory history = facade().myHistory(currentUser(request));

        request.setAttribute("history", history);
        request.setAttribute("patient", history.getPatient());
        request.setAttribute("appointments", history.getAppointments());

        render(request, response, "my-appointments", "My appointments", "/app/my/appointments");
    }
}
