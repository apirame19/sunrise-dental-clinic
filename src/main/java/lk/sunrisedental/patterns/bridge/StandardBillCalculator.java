package lk.sunrisedental.patterns.bridge;

import lk.sunrisedental.util.Money;

import java.math.BigDecimal;

/**
 * BRIDGE - refined abstraction: the clinic's ordinary billing terms.
 *
 * <p>The treatment is charged at list price, one consultation fee is added, and the health service
 * levy is applied to the sum for treatments that are not exempt. No discount.</p>
 *
 * <p>The levy is charged on treatment plus consultation rather than on the treatment alone,
 * because the consultation is itself a chargeable service.</p>
 */
public class StandardBillCalculator extends BillCalculator {

    private static final String POLICY_NAME = "Standard";

    public StandardBillCalculator(TreatmentPricingProvider pricing) {
        super(pricing);
    }

    @Override
    public BillingPlan plan(BillingContext context) {
        TreatmentPrice price = requirePrice(context.treatmentId());

        BigDecimal treatmentCost = Money.scale(price.baseCost());
        BigDecimal consultationFee = Money.scale(pricing.consultationFee());
        BigDecimal discount = Money.ZERO;

        // Exempt treatments produce a zero levy, which the Decorator then omits as a line
        // rather than printing "Tax 0.00" on every preventive receipt.
        BigDecimal taxable = Money.sum(treatmentCost, consultationFee);
        BigDecimal tax = price.taxable()
                ? Money.percentOf(taxable, pricing.taxRatePercent())
                : Money.ZERO;

        return new BillingPlan(price, treatmentCost, consultationFee, discount, tax, POLICY_NAME);
    }

    @Override
    public String getPolicyName() {
        return POLICY_NAME;
    }
}
