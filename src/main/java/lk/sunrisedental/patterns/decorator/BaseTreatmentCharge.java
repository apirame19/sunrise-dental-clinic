package lk.sunrisedental.patterns.decorator;

import lk.sunrisedental.model.BillLine;
import lk.sunrisedental.model.BillLineType;
import lk.sunrisedental.patterns.bridge.BillingPlan;
import lk.sunrisedental.util.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * DECORATOR - the concrete component that everything else wraps.
 *
 * <p>The treatment's own charge, taken from the {@link BillingPlan} the Bridge produced. This is
 * where the two patterns meet: the Bridge decided the figure, and from here on the Decorator chain
 * is only concerned with composing the receipt.</p>
 *
 * <p>Always present. Every bill is for a treatment, so unlike the decorators above it there is no
 * case in which this component is omitted.</p>
 */
public class BaseTreatmentCharge implements BillComponent {

    private final BillingPlan plan;

    /**
     * @param plan the figures decided by the billing policy
     */
    public BaseTreatmentCharge(BillingPlan plan) {
        this.plan = plan;
    }

    @Override
    public BigDecimal getAmount() {
        return Money.scale(plan.treatmentCost());
    }

    @Override
    public List<BillLine> getLines() {
        return List.of(new BillLine(1, BillLineType.TREATMENT,
                plan.treatment().describe(), getAmount()));
    }

    @Override
    public String getDescription() {
        return plan.treatment().describe();
    }
}
