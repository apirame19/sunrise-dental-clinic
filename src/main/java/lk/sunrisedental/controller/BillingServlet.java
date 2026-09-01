package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.model.Bill;
import lk.sunrisedental.model.User;

import java.io.IOException;
import java.util.Optional;

/**
 * The billing screens: {@code /app/billing/*}.
 *
 * <p>The flow is deliberately preview-then-issue. A receptionist looks up an appointment, sees the
 * itemised breakdown the Decorator chain produced - treatment, consultation, any follow-up
 * discount, the levy - and only then issues the bill. Issuing is irreversible: there is no screen
 * in this application that deletes a bill, because a receipt handed to a patient is a record of
 * what they were charged and quietly unmaking it is how a clinic loses track of its own takings.
 * A mistake is corrected by a bookkeeping entry, not by deletion.</p>
 *
 * <p>The preview and the issued bill are produced by the same {@code BillAssembler} over the same
 * {@code BillingPlan}, so what the patient is quoted and what they are charged cannot differ. A
 * preview that ran its own simplified calculation would be exactly the billing error the clinic
 * asked to be rid of.</p>
 *
 * <p>{@code receipt} renders the stored bill on its own, without the navigation, so the browser's
 * print command produces something that can be handed over.</p>
 */
@WebServlet(name = "BillingServlet", urlPatterns = {"/app/billing", "/app/billing/*"})
public class BillingServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        switch (action(request)) {
            case "receipt" -> showReceipt(request, response);
            case "", "preview" -> showPreview(request, response);
            default -> throw badRequest("There is no billing page at that address.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!"generate".equals(action(request))) {
            throw badRequest("That billing action is not recognised.");
        }
        generate(request, response);
    }

    // ------------------------------------------------------------------ preview

    private void showPreview(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        String appointmentNo = param(request, "no");

        request.setAttribute("searchTerm", appointmentNo);
        request.setAttribute("currency", facade().configuration().getCurrencyCode());

        if (appointmentNo != null) {
            // An issued bill takes precedence over a preview. Showing a fresh calculation for an
            // appointment that has already been billed would invite somebody to try to bill it
            // again, and would show different figures if a price had changed since.
            Optional<Bill> issued = facade().findBillForAppointment(actor, appointmentNo);

            if (issued.isPresent()) {
                request.setAttribute("bill", issued.get());
                request.setAttribute("alreadyIssued", Boolean.TRUE);
            } else {
                request.setAttribute("appointment", facade().findAppointment(actor, appointmentNo));
                request.setAttribute("bill", facade().previewBill(actor, appointmentNo));
                request.setAttribute("alreadyIssued", Boolean.FALSE);
            }
            request.setAttribute("searched", Boolean.TRUE);
        }

        render(request, response, "billing/preview", "Billing", "/app/billing");
    }

    // ------------------------------------------------------------------ issue

    private void generate(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String appointmentNo = param(request, "no");
        if (appointmentNo == null) {
            throw badRequest("Please give an appointment number to bill.");
        }

        try {
            Bill bill = facade().generateBill(currentUser(request), appointmentNo);

            flash(request, true, "Bill " + bill.getBillNo() + " has been issued.");
            redirect(request, response, "/app/billing/receipt?billNo=" + encode(bill.getBillNo()));

        } catch (BusinessRuleException e) {
            // "Not completed", "already billed" and "cancelled" all arrive here. The user needs to
            // stay on the billing screen with the appointment still loaded, not be sent to an
            // error page they have to navigate back from.
            flash(request, false, e.getUserMessage());
            redirect(request, response, "/app/billing?no=" + encode(appointmentNo));
        }
    }

    // ------------------------------------------------------------------ receipt

    private void showReceipt(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);
        String billNo = param(request, "billNo");
        if (billNo == null) {
            throw badRequest("Please give a bill number.");
        }

        Bill bill = facade().findBill(actor, billNo)
                .orElseThrow(() -> badRequest("No bill was found with the number " + billNo + "."));

        request.setAttribute("bill", bill);
        request.setAttribute("currency", facade().configuration().getCurrencyCode());
        request.setAttribute("clinic", facade().configuration());

        // Forwarded directly rather than through render(): the receipt is a standalone document
        // with no navigation, so that printing it produces something a patient can be handed.
        request.getRequestDispatcher("/WEB-INF/views/billing/receipt.jsp")
                .forward(request, response);
    }
}
