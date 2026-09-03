package lk.sunrisedental.patterns.decorator;

import lk.sunrisedental.model.BillLineType;
import lk.sunrisedental.patterns.bridge.BillingPlan;
import lk.sunrisedental.util.Money;

import java.math.BigDecimal;

/**
 * DECORATOR - adds the clinic's consultation fee.
 *
 * <p>The brief requires the consultation fee to be part of the total, and it is charged once per
 * bill regardless of the treatment. When the billing policy has waived it - a follow-up visit -
 * the amount is zero and the base class omits the line entirely, so the receipt does not carry a
 * puzzling "Consultation 0.00".</p>
 */
public class ConsultationFeeDecorator extends BillDecorator {

    private final BillingPlan plan;

    /**
     * @param wrapped the component this fee is added to
     * @param plan    the figures decided by the billing policy
     */
    public ConsultationFeeDecorator(BillComponent wrapped, BillingPlan plan) {
        super(wrapped);
        this.plan = plan;
    }

    @Override
    protected BigDecimal ownAmount() {
        return Money.scale(plan.consultationFee());
    }

    @Override
    protected BillLineType ownLineType() {
        return BillLineType.CONSULTATION;
    }

    @Override
    protected String ownDescription() {
        return "Consultation fee";
    }
}
