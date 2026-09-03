package lk.sunrisedental.patterns.bridge;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * BRIDGE - the implementor.
 *
 * <p>This is the "where do prices come from" axis of the Bridge. It is entirely independent of the
 * "which billing policy applies" axis represented by {@link BillCalculator}, and that independence
 * is the whole point: a new billing policy needs no new price source, and a new price source needs
 * no new policy. Without the Bridge, policies multiplied by sources would mean a class for every
 * combination.</p>
 *
 * <p>Two implementations exist, and both are used in earnest:</p>
 * <ul>
 *   <li>{@link JdbcTreatmentPricingProvider} reads the live price list and clinic settings, and is
 *       what runs in production.</li>
 *   <li>{@link InMemoryTreatmentPricingProvider} holds a fixed table, and is what makes the entire
 *       billing calculation testable without a database. That is not a convenience - it is what
 *       allows billing, the part of the system where a defect costs the clinic money, to be
 *       developed test-first at all.</li>
 * </ul>
 *
 * <p>Rates as well as prices come through here. A test that could pin the treatment cost but not
 * the tax rate could not assert an exact total.</p>
 */
public interface TreatmentPricingProvider {

    /**
     * @param treatmentId the treatment to price
     * @return its price details, or empty if no such treatment exists
     */
    Optional<TreatmentPrice> findPrice(int treatmentId);

    /** @return the consultation fee the clinic charges once per bill. */
    BigDecimal consultationFee();

    /** @return the health service levy as a percentage, for example {@code 2.50}. */
    BigDecimal taxRatePercent();

    /** @return the discount given on a qualifying follow-up visit, as a percentage. */
    BigDecimal followUpDiscountPercent();
}
