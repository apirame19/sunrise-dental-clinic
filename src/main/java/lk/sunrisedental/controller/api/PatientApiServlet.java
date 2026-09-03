package lk.sunrisedental.controller.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.model.Patient;
import lk.sunrisedental.model.User;
import lk.sunrisedental.util.JsonWriter;

import java.io.IOException;
import java.util.List;

/**
 * Patients over JSON: {@code /api/patients/*}.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code GET  /api/patients}</td><td>every patient</td></tr>
 *   <tr><td>{@code GET  /api/patients?q=}</td><td>search by name or number</td></tr>
 *   <tr><td>{@code GET  /api/patients/history?id=}</td><td>the complete record</td></tr>
 *   <tr><td>{@code POST /api/patients}</td><td>register one</td></tr>
 * </table>
 *
 * <p>The history endpoint is the one worth having. It returns every visit, its outcome and its
 * bill in a single response, assembled by the business tier - which is the same assembly the
 * patient screen renders, so the two cannot show different histories for the same person.</p>
 */
@WebServlet(name = "PatientApiServlet", urlPatterns = {"/api/patients", "/api/patients/*"})
public class PatientApiServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User actor = currentUser(request);

        switch (action(request)) {
            case "history" -> history(request, response, actor);
            case "" -> list(request, response, actor);
            default -> throw new BusinessRuleException("NO_SUCH_ENDPOINT",
                    "There is no patient endpoint at that address.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        if (!action(request).isEmpty()) {
            throw new BusinessRuleException("NO_SUCH_ENDPOINT",
                    "There is no patient endpoint at that address.");
        }

        User actor = currentUser(request);
        Patient saved = facade().registerPatient(actor,
                param(request, "patientName"),
                param(request, "address"),
                param(request, "contactNumber"),
                param(request, "email"));

        write(response, HttpServletResponse.SC_CREATED, JsonWriter.object()
                .add("patient", JsonModels.patient(saved)));
    }

    private void list(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        String term = param(request, "q");
        List<Patient> patients = term == null
                ? facade().allPatients(actor)
                : facade().searchPatients(actor, term);

        ok(response, JsonWriter.object()
                .add("query", term)
                .add("count", patients.size())
                .add("patients", JsonModels.arrayOf(patients, JsonModels::patient)));
    }

    private void history(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        Integer patientId = optionalIntParam(request, "id");
        if (patientId == null) {
            throw new BusinessRuleException("BAD_REQUEST", "Give the patient id as ?id=");
        }

        ok(response, JsonModels.patientHistory(facade().patientHistory(actor, patientId)));
    }
}
