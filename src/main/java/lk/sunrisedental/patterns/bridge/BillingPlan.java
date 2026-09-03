package lk.sunrisedental.patterns.bridge;

import lk.sunrisedental.util.Money;

import java.math.BigDecimal;

/**
 * The outcome of a billing policy: what each component costs, before anything is itemised.
 *
 * <p>This is the boundary between the two billing patterns, and keeping it sharp is what stops
 * them overlapping:</p>
 *
 * <ul>
 *   <li>The <strong>Bridge</strong> decides the figures - which components apply, at what rates,
 *       from which price source - and produces one of these.</li>
 *   <li>The <strong>Decorator</strong> takes this and builds the itemised bill, wrapping a base
 *       charge in the components that are non-zero, in receipt order.</li>
 * </ul>
 *
 * <p>Immutable, and every amount already scaled to two places by the calculator that produced it.
 * A plan cannot describe an inconsistent bill: {@link #total()} is derived rather than stored, so
 * the parts and the whole cannot drift apart.</p>
 *
 * @param treatment        which treatment was priced, for the receipt line
 * @param treatmentCost    the treatment charge
 * @param consultationFee  the consultation charge; zero when waived
 * @param discountAmount   the reduction; zero when none applies
 * @param taxAmount        the health service levy; zero for exempt treatments
 * @param policyName       the policy that produced this, for display and audit
 */
public record BillingPlan(TreatmentPrice treatment,
                          BigDecimal treatmentCost,
                          BigDecimal consultationFee,
                          BigDecimal discountAmount,
                          BigDecimal taxAmount,
                          String policyName) {

    /**
     * @return treatment + consultation - discount + tax, scaled and never negative.
     *         Derived rather than stored: a total that could be set independently of its
     *         components is exactly the billing error this system exists to prevent.
     */
    public BigDecimal total() {
        return Money.notNegative(
                treatmentCost.add(consultationFee).subtract(discountAmount).add(taxAmount));
    }

    /** @return the charges the discount and levy are computed against. */
    public BigDecimal grossCharges() {
        return Money.sum(treatmentCost, consultationFee);
    }

    /** @return the amount after any discount, before the levy. */
    public BigDecimal netBeforeTax() {
        return Money.notNegative(grossCharges().subtract(discountAmount));
    }

    /** @return {@code true} if a consultation fee is being charged. */
    public boolean hasConsultationFee() {
        return consultationFee.signum() > 0;
    }

    /** @return {@code true} if a discount applies. */
    public boolean hasDiscount() {
        return discountAmount.signum() > 0;
    }

    /** @return {@code true} if the levy applies. */
    public boolean hasTax() {
        return taxAmount.signum() > 0;
    }
}
