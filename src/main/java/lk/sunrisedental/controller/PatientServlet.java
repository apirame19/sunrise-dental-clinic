package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.model.Patient;
import lk.sunrisedental.model.User;
import lk.sunrisedental.validation.ValidationResult;

import java.io.IOException;

/**
 * The patient screens: {@code /app/patients/*}.
 *
 * <p>{@code view} is the direct replacement for the paper file that used to go missing: one page
 * carrying every visit, its outcome and its bill, assembled by the business tier in a single call
 * so the view issues no queries of its own.</p>
 *
 * <p>Registration here is for a patient who is not being booked in at the same moment - a walk-in
 * being put on file, say. The usual route is the booking form, which creates the patient record
 * as part of the appointment; both go through the same validation handlers, so a telephone number
 * is normalised identically whichever screen it arrives through. That matters: it is what stops
 * "077 123 4567" and "0771234567" becoming two people with half a history each.</p>
 */
@WebServlet(name = "PatientServlet", urlPatterns = {"/app/patients", "/app/patients/*"})
public class PatientServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        switch (action(request)) {
            case "register" -> showRegisterForm(request, response);
            case "view" -> showHistory(request, response);
            case "edit" -> showEditForm(request, response);
            case "" -> showList(request, response);
            default -> throw badRequest("There is no patient page at that address.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        switch (action(request)) {
            case "register" -> register(request, response);
            case "edit" -> update(request, response);
            default -> throw badRequest("That patient action is not recognised.");
        }
    }

    // ------------------------------------------------------------------ list and search

    private void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        String term = param(request, "q");

        // A blank search shows everyone; a term narrows it. The service returns nothing at all for
        // a blank term, so the choice of which call to make is made here rather than there.
        request.setAttribute("patients", term == null
                ? facade().allPatients(actor)
                : facade().searchPatients(actor, term));
        request.setAttribute("searchTerm", term);

        render(request, response, "patients/list", "Patients", "/app/patients");
    }

    private void showHistory(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        int patientId = requiredId(request);

        request.setAttribute("history", facade().patientHistory(actor, patientId));
        // The same history as a Composite tree, grouped by year, so the page can offer a printable
        // record without a second shape of the same data.
        request.setAttribute("report", facade().patientHistoryReport(actor, patientId));
        request.setAttribute("currency", facade().configuration().getCurrencyCode());

        render(request, response, "patients/view", "Patient record", "/app/patients");
    }

    // ------------------------------------------------------------------ registration

    private void showRegisterForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        render(request, response, "patients/register", "Register patient", "/app/patients");
    }

    private void register(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        String name = param(request, "patientName");
        String address = param(request, "address");
        String contact = param(request, "contactNumber");
        String email = param(request, "email");

        try {
            Patient saved = facade().registerPatient(actor, name, address, contact, email);

            flash(request, true, saved.getPatientName() + " has been registered.");
            redirect(request, response, "/app/patients/view?id=" + saved.getPatientId());

        } catch (ValidationException e) {
            redisplayRegister(request, response, e.getValidationResult(),
                    name, address, contact, email, HttpServletResponse.SC_BAD_REQUEST);

        } catch (BusinessRuleException e) {
            // "Already registered" is the expected one here, and it is about the clinic's records
            // rather than about a single field, so it is shown as a banner.
            ValidationResult result = new ValidationResult();
            result.addGlobalError(e.getUserMessage());
            redisplayRegister(request, response, result,
                    name, address, contact, email, HttpServletResponse.SC_CONFLICT);
        }
    }

    private void redisplayRegister(HttpServletRequest request, HttpServletResponse response,
                                   ValidationResult validation, String name, String address,
                                   String contact, String email, int status)
            throws ServletException, IOException {

        response.setStatus(status);
        request.setAttribute("validation", validation);
        request.setAttribute("patientName", name);
        request.setAttribute("address", address);
        request.setAttribute("contactNumber", contact);
        request.setAttribute("email", email);

        render(request, response, "patients/register", "Register patient", "/app/patients");
    }

    // ------------------------------------------------------------------ amendment

    private void showEditForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setAttribute("patient", facade().findPatient(currentUser(request), requiredId(request)));
        render(request, response, "patients/edit", "Amend patient details", "/app/patients");
    }

    private void update(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        int patientId = requiredId(request);

        Patient submitted = facade().findPatient(actor, patientId);
        // The name is deliberately not editable: it forms half of the patient identity key, so
        // changing it here would silently split or merge records.
        submitted.setAddress(param(request, "address"));
        submitted.setContactNumber(param(request, "contactNumber"));
        submitted.setEmail(param(request, "email"));

        try {
            Patient saved = facade().updatePatientDetails(actor, submitted);

            flash(request, true, "The details for " + saved.getPatientName() + " have been updated.");
            redirect(request, response, "/app/patients/view?id=" + patientId);

        } catch (ValidationException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("validation", e.getValidationResult());
            request.setAttribute("patient", submitted);
            render(request, response, "patients/edit", "Amend patient details", "/app/patients");
        }
    }

    /** @return the {@code id} parameter, refusing the request if it is absent or not a number. */
    private static int requiredId(HttpServletRequest request) {
        Integer patientId = optionalIntParam(request, "id");
        if (patientId == null) {
            throw new BusinessRuleException("BAD_REQUEST", "Please give a patient to look at.");
        }
        return patientId;
    }
}
