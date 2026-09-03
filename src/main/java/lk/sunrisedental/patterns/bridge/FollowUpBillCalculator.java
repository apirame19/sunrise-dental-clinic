package lk.sunrisedental.patterns.bridge;

import lk.sunrisedental.util.Money;

import java.math.BigDecimal;

/**
 * BRIDGE - refined abstraction: terms for a return visit.
 *
 * <p>Applies when a patient comes back for the same treatment within the configured follow-up
 * window. Two things change, and both are decisions the clinic makes rather than arithmetic
 * details:</p>
 *
 * <ul>
 *   <li><strong>The consultation fee is waived.</strong> The patient was assessed on the first
 *       visit; charging to assess them again for the same problem is what patients complain
 *       about. This is a structural difference - the receipt comes out with one fewer line.</li>
 *   <li><strong>The treatment is discounted</strong> by the configured percentage.</li>
 * </ul>
 *
 * <p>The levy is charged on the discounted amount, not the list price, because tax follows what
 * the patient is actually asked to pay.</p>
 *
 * <p>The discount is capped at the charges it applies to, so a misconfigured percentage above 100
 * cannot produce a negative bill. The database enforces the same rule ({@code SDC-202}); this
 * check exists so the clinic never gets that far.</p>
 */
public class FollowUpBillCalculator extends BillCalculator {

    private static final String POLICY_NAME = "Follow-up visit";

    public FollowUpBillCalculator(TreatmentPricingProvider pricing) {
        super(pricing);
    }

    @Override
    public BillingPlan plan(BillingContext context) {
        TreatmentPrice price = requirePrice(context.treatmentId());

        BigDecimal treatmentCost = Money.scale(price.baseCost());
        BigDecimal consultationFee = Money.ZERO;   // waived on a return visit

        BigDecimal charges = Money.sum(treatmentCost, consultationFee);
        BigDecimal discount = Money.cappedAt(
                Money.percentOf(charges, pricing.followUpDiscountPercent()), charges);

        BigDecimal netOfDiscount = Money.notNegative(charges.subtract(discount));
        BigDecimal tax = price.taxable()
                ? Money.percentOf(netOfDiscount, pricing.taxRatePercent())
                : Money.ZERO;

        return new BillingPlan(price, treatmentCost, consultationFee, discount, tax, POLICY_NAME);
    }

    @Override
    public String getPolicyName() {
        return POLICY_NAME;
    }
}
