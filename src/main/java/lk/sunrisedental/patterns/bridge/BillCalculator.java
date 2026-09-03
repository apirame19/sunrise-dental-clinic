package lk.sunrisedental.patterns.bridge;

import java.util.Objects;

/**
 * BRIDGE - the abstraction.
 *
 * <p>Holds a {@link TreatmentPricingProvider} rather than extending one. That composition is the
 * Bridge: the billing policy hierarchy and the price-source hierarchy vary independently and are
 * combined at run time.</p>
 *
 * <p>Two policies exist, and they differ in structure rather than merely in a percentage:</p>
 * <ul>
 *   <li>{@link StandardBillCalculator} - treatment plus a consultation fee, levy on the sum.</li>
 *   <li>{@link FollowUpBillCalculator} - a return visit inside the configured window. The clinic
 *       waives the consultation fee entirely, because the patient is not being newly assessed,
 *       <em>and</em> discounts the treatment.</li>
 * </ul>
 *
 * <p>That second policy is why this is a Bridge and not a strategy over a single rate. Waiving a
 * component is a different shape of bill, not a different number in the same shape, and the
 * itemised receipt has to come out with one fewer line.</p>
 *
 * <p>Subclasses implement {@link #plan(BillingContext)} and nothing else. Looking a price up and
 * failing usefully when it is missing is handled here, so no policy can quietly bill zero for a
 * treatment it could not find.</p>
 */
public abstract class BillCalculator {

    /** The price source. Protected so refinements can read prices, final so none can swap it. */
    protected final TreatmentPricingProvider pricing;

    /**
     * @param pricing where prices and rates come from
     */
    protected BillCalculator(TreatmentPricingProvider pricing) {
        this.pricing = Objects.requireNonNull(pricing, "pricing provider");
    }

    /**
     * Works out what to charge.
     *
     * @param context the treatment being billed and whether follow-up terms apply
     * @return the figures, ready for the Decorator chain to itemise
     * @throws IllegalArgumentException if the treatment does not exist
     */
    public abstract BillingPlan plan(BillingContext context);

    /**
     * Looks up a treatment, refusing to continue if it is unknown.
     *
     * <p>Returning a zero price for a missing treatment would produce a bill that looks valid and
     * undercharges silently. Failing here means the problem surfaces at once.</p>
     *
     * @param treatmentId the treatment to price
     * @return its price details
     * @throws IllegalArgumentException if no such treatment exists
     */
    protected TreatmentPrice requirePrice(int treatmentId) {
        return pricing.findPrice(treatmentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No price is on file for treatment " + treatmentId
                        + ", so a bill cannot be produced."));
    }

    /** @return the name of this policy, shown on the bill and used in the audit trail. */
    public abstract String getPolicyName();
}
