package lk.sunrisedental.patterns.decorator;

import lk.sunrisedental.model.BillLineType;
import lk.sunrisedental.patterns.bridge.BillingPlan;
import lk.sunrisedental.util.Money;

import java.math.BigDecimal;

/**
 * DECORATOR - adds the health service levy.
 *
 * <p>Applied last, so it lands beneath the discount on the receipt and reads in the order the
 * arithmetic was done. Preventive treatments are exempt, in which case the amount is zero and the
 * line is omitted rather than printed as "0.00".</p>
 *
 * <p>The levy amount is computed by the billing policy, which knows whether it was charged on the
 * gross or the discounted figure. This component does not recalculate it - two places computing
 * the same tax is how a receipt ends up disagreeing with the stored bill.</p>
 */
public class TaxDecorator extends BillDecorator {

    private final BillingPlan plan;
    private final BigDecimal ratePercent;

    /**
     * @param wrapped     the component the levy is added to
     * @param plan        the figures decided by the billing policy
     * @param ratePercent the rate, shown on the receipt line
     */
    public TaxDecorator(BillComponent wrapped, BillingPlan plan, BigDecimal ratePercent) {
        super(wrapped);
        this.plan = plan;
        this.ratePercent = ratePercent;
    }

    /**
     * @param wrapped the component the levy is added to
     * @param plan    the figures decided by the billing policy
     */
    public TaxDecorator(BillComponent wrapped, BillingPlan plan) {
        this(wrapped, plan, null);
    }

    @Override
    protected BigDecimal ownAmount() {
        return Money.scale(plan.taxAmount());
    }

    @Override
    protected BillLineType ownLineType() {
        return BillLineType.TAX;
    }

    @Override
    protected String ownDescription() {
        if (ratePercent == null || ratePercent.signum() == 0) {
            return "Health service levy";
        }
        return "Health service levy @ " + ratePercent.stripTrailingZeros().toPlainString() + "%";
    }
}
