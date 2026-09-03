package lk.sunrisedental.billing;

import lk.sunrisedental.model.Bill;
import lk.sunrisedental.patterns.bridge.BillingPlan;
import lk.sunrisedental.patterns.decorator.BaseTreatmentCharge;
import lk.sunrisedental.patterns.decorator.BillComponent;
import lk.sunrisedental.patterns.decorator.ConsultationFeeDecorator;
import lk.sunrisedental.patterns.decorator.DiscountDecorator;
import lk.sunrisedental.patterns.decorator.TaxDecorator;
import lk.sunrisedental.util.Money;

import java.math.BigDecimal;

/**
 * Turns a {@link BillingPlan} into an itemised {@link Bill} using the Decorator chain.
 *
 * <p>This is the seam between the two billing patterns. The Bridge has already decided the
 * figures; this class decides how they are composed into a receipt, by wrapping a base charge in
 * the components that actually apply:</p>
 *
 * <pre>
 *   BaseTreatmentCharge
 *     └─ ConsultationFeeDecorator     (omits its line when the fee is waived)
 *          └─ DiscountDecorator       (omits its line when no discount applies)
 *               └─ TaxDecorator       (omits its line when the treatment is exempt)
 * </pre>
 *
 * <p><strong>The order is arithmetic, not presentation.</strong> The consultation fee is added
 * before the discount because a follow-up discount is calculated on the charges as a whole, and
 * the levy is added last because it is charged on the discounted amount. Reordering these
 * decorators would change what the patient pays.</p>
 *
 * <p>Every decorator is applied unconditionally. A component that does not apply contributes zero
 * and omits its own line, so which lines appear is decided by the figures rather than by a
 * conditional here. That keeps the assembly declarative and means a new component is added by
 * writing one class and one line.</p>
 */
public class BillAssembler {

    /**
     * Builds the itemised bill.
     *
     * @param plan the figures decided by the billing policy
     * @return a bill carrying its component amounts, its total and its receipt lines. It has no
     *         bill number or appointment yet; {@code BillingService} supplies those when it
     *         persists the bill.
     * @throws IllegalStateException if the assembled lines do not sum to the total, which would
     *                               mean a defect in a decorator
     */
    public Bill assemble(BillingPlan plan) {
        BillComponent component = new BaseTreatmentCharge(plan);
        component = new ConsultationFeeDecorator(component, plan);
        component = new DiscountDecorator(component, plan);
        component = new TaxDecorator(component, plan, taxRateFor(plan));

        Bill bill = new Bill();
        bill.setTreatmentCost(Money.scale(plan.treatmentCost()));
        bill.setConsultationFee(Money.scale(plan.consultationFee()));
        bill.setDiscountAmount(Money.scale(plan.discountAmount()));
        bill.setTaxAmount(Money.scale(plan.taxAmount()));
        bill.setTotalAmount(Money.scale(plan.total()));
        bill.setLines(component.getLines());

        // The decorator traversal and the plan's own arithmetic are independent routes to the
        // same number. If they ever disagree, something is wrong in a decorator and the bill must
        // not be issued - a receipt whose lines do not add up is the exact failure this system
        // was commissioned to eliminate.
        if (!bill.isInternallyConsistent()) {
            throw new IllegalStateException(
                    "Assembled bill is inconsistent: lines total " + bill.getLineSum()
                    + " but the bill total is " + bill.getTotalAmount());
        }

        // The decorator chain's accumulated amount is a third check on the same figure.
        if (component.getAmount().compareTo(bill.getTotalAmount()) != 0) {
            throw new IllegalStateException(
                    "Decorator chain produced " + component.getAmount()
                    + " but the billing plan totals " + bill.getTotalAmount());
        }

        return bill;
    }

    /**
     * @return the levy rate to print on the receipt line, derived from the amounts the policy
     *         produced, or {@code null} when no levy applies
     */
    private static BigDecimal taxRateFor(BillingPlan plan) {
        if (!plan.hasTax()) {
            return null;
        }
        BigDecimal base = plan.netBeforeTax();
        if (base.signum() == 0) {
            return null;
        }
        return plan.taxAmount()
                .multiply(new BigDecimal("100"))
                .divide(base, 2, Money.ROUNDING);
    }
}
