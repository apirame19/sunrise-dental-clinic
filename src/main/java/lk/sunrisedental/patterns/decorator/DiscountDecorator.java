package lk.sunrisedental.patterns.decorator;

import lk.sunrisedental.model.BillLineType;
import lk.sunrisedental.patterns.bridge.BillingPlan;
import lk.sunrisedental.util.Money;

import java.math.BigDecimal;

/**
 * DECORATOR - applies a reduction.
 *
 * <p>The only component that contributes a <strong>negative</strong> amount. Storing the discount
 * as a negative line rather than as a positive number to be subtracted elsewhere means the lines
 * on a receipt always sum to its total, with no special case for deductions. That property is
 * asserted directly in the billing tests and mirrored by the {@code SDC-202} database guard.</p>
 *
 * <p>The amount itself, and whether a discount applies at all, is decided by the billing policy;
 * this component only places it on the receipt.</p>
 */
public class DiscountDecorator extends BillDecorator {

    private final BillingPlan plan;

    /**
     * @param wrapped the component the discount is applied to
     * @param plan    the figures decided by the billing policy
     */
    public DiscountDecorator(BillComponent wrapped, BillingPlan plan) {
        super(wrapped);
        this.plan = plan;
    }

    @Override
    protected BigDecimal ownAmount() {
        return Money.scale(plan.discountAmount()).negate();
    }

    @Override
    protected BillLineType ownLineType() {
        return BillLineType.DISCOUNT;
    }

    @Override
    protected String ownDescription() {
        return plan.policyName() + " discount";
    }
}
