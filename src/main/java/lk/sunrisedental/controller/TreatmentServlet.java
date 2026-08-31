package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.model.Treatment;
import lk.sunrisedental.model.User;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The treatment catalogue and price list: {@code GET /app/treatments}.
 *
 * <p>Read-only, and there is no screen behind it that is not. A treatment's price is referenced by
 * every bill ever issued for it, so changing one is a controlled data-administration task rather
 * than something the front desk can do between telephone calls. Bills store their own copy of the
 * figures, so a later price change never alters a receipt already handed to a patient - but
 * keeping price edits out of the running application is what makes that easy to be sure of.</p>
 *
 * <p>The indicative total shown against each treatment is list price plus consultation fee plus
 * the levy where it applies. It is computed by the business tier, not by the JSP: a view that did
 * its own arithmetic would be a second implementation of the billing rules, and the two would
 * eventually disagree about a rounding.</p>
 */
@WebServlet(name = "TreatmentServlet", urlPatterns = {"/app/treatments", "/app/treatments/*"})
public class TreatmentServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!action(request).isEmpty()) {
            throw badRequest("There is no treatment page at that address.");
        }

        User actor = currentUser(request);
        // false: discontinued treatments are listed too, so an old bill referring to one still
        // makes sense to whoever is looking at it.
        List<Treatment> treatments = facade().treatments(actor, false);

        Map<Integer, BigDecimal> indicative = new LinkedHashMap<>();
        for (Treatment treatment : treatments) {
            indicative.put(treatment.getTreatmentId(), facade().indicativeTotal(treatment));
        }

        request.setAttribute("treatments", treatments);
        request.setAttribute("indicativeTotals", indicative);
        request.setAttribute("consultationFee", facade().configuration().getConsultationFee());
        request.setAttribute("taxRate", facade().configuration().getTaxRatePercent());
        request.setAttribute("currency", facade().configuration().getCurrencyCode());

        render(request, response, "treatments/list", "Treatments and prices", "/app/treatments");
    }
}
