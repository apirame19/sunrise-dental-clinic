package lk.sunrisedental.controller.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.model.Bill;
import lk.sunrisedental.model.User;
import lk.sunrisedental.util.JsonWriter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Billing over JSON: {@code /api/bills/*}.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code GET  /api/bills/preview?no=}</td><td>what a visit would cost, saving nothing</td></tr>
 *   <tr><td>{@code GET  /api/bills?billNo=}</td><td>an issued bill and its lines</td></tr>
 *   <tr><td>{@code GET  /api/bills?from=&to=}</td><td>bills issued in a range - administrators</td></tr>
 *   <tr><td>{@code POST /api/bills}</td><td>issue a bill for a completed visit</td></tr>
 * </table>
 *
 * <p>Preview and issue produce their figures through the same Bridge calculator and the same
 * Decorator chain, so a quote given over the telephone and the bill later handed over cannot
 * differ. The whole of that arithmetic lives in the business tier; nothing in this class adds,
 * multiplies or rounds anything.</p>
 *
 * <p>There is no endpoint that deletes a bill, for the same reason there is no screen that does.
 * A receipt is a record of what a patient was charged, and quietly unmaking one is how a clinic
 * loses track of its own takings.</p>
 */
@WebServlet(name = "BillApiServlet", urlPatterns = {"/api/bills", "/api/bills/*"})
public class BillApiServlet extends ApiServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        User actor = currentUser(request);

        switch (action(request)) {
            case "preview" -> preview(request, response, actor);
            case "" -> lookup(request, response, actor);
            default -> throw new BusinessRuleException("NO_SUCH_ENDPOINT",
                    "There is no billing endpoint at that address.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        if (!action(request).isEmpty()) {
            throw new BusinessRuleException("NO_SUCH_ENDPOINT",
                    "There is no billing endpoint at that address.");
        }

        String appointmentNo = param(request, "appointmentNo");
        if (appointmentNo == null) {
            throw new BusinessRuleException("BAD_REQUEST",
                    "Give the appointment to bill as appointmentNo=");
        }

        Bill issued = facade().generateBill(currentUser(request), appointmentNo);

        write(response, HttpServletResponse.SC_CREATED, JsonWriter.object()
                .add("bill", JsonModels.bill(issued)));
    }

    private void preview(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        String appointmentNo = param(request, "no");
        if (appointmentNo == null) {
            throw new BusinessRuleException("BAD_REQUEST",
                    "Give the appointment number as ?no=");
        }

        Optional<Bill> issued = facade().findBillForAppointment(actor, appointmentNo);

        // An issued bill wins over a fresh calculation. Returning a preview for an appointment
        // that has already been billed would show different figures if a price had changed since,
        // and would invite a caller to try to bill it twice.
        ok(response, JsonWriter.object()
                .add("appointmentNo", appointmentNo)
                .add("issued", issued.isPresent())
                .add("bill", JsonModels.bill(
                        issued.orElseGet(() -> facade().previewBill(actor, appointmentNo)))));
    }

    private void lookup(HttpServletRequest request, HttpServletResponse response, User actor)
            throws IOException {

        String billNo = param(request, "billNo");

        if (billNo != null) {
            Optional<Bill> bill = facade().findBill(actor, billNo);
            if (bill.isEmpty()) {
                write(response, HttpServletResponse.SC_NOT_FOUND, JsonWriter.object()
                        .add("error", "BILL_NOT_FOUND")
                        .add("message", "No bill was found with the number " + billNo + "."));
                return;
            }
            ok(response, JsonWriter.object().add("bill", JsonModels.bill(bill.get())));
            return;
        }

        LocalDate from = dateParam(request, "from", facade().today());
        LocalDate to = dateParam(request, "to", facade().today());

        // Administrators only - the facade refuses this to anyone else, and refuses it identically
        // for the HTML screen.
        List<Bill> bills = facade().billsBetween(actor, from, to);

        ok(response, JsonWriter.object()
                .add("from", from.toString())
                .add("to", to.toString())
                .add("count", bills.size())
                .add("bills", JsonModels.arrayOf(bills, JsonModels::bill)));
    }
}
