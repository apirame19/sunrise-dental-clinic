package lk.sunrisedental.patterns.bridge;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * BRIDGE - concrete implementor backed by a fixed in-memory price table.
 *
 * <p>This is the implementor that makes the Bridge worth having. Because the pricing axis is
 * separate from the policy axis, the entire billing calculation can be exercised against a price
 * table declared in three lines of a test - no MySQL, no schema, no fixtures, no Tomcat. Billing
 * is the part of this system where a defect costs the clinic money, and this is what allowed it to
 * be written test-first.</p>
 *
 * <p>It is also the fallback if the price list ever has to be pinned for a demonstration or a
 * migration, so it is production code rather than a test fixture.</p>
 *
 * <p>Instances are immutable once built and therefore safe to share.</p>
 */
public final class InMemoryTreatmentPricingProvider implements TreatmentPricingProvider {

    private final Map<Integer, TreatmentPrice> prices;
    private final BigDecimal consultationFee;
    private final BigDecimal taxRatePercent;
    private final BigDecimal followUpDiscountPercent;

    private InMemoryTreatmentPricingProvider(Builder builder) {
        this.prices = Map.copyOf(builder.prices);
        this.consultationFee = builder.consultationFee;
        this.taxRatePercent = builder.taxRatePercent;
        this.followUpDiscountPercent = builder.followUpDiscountPercent;
    }

    /** @return a builder for assembling a price table. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<TreatmentPrice> findPrice(int treatmentId) {
        return Optional.ofNullable(prices.get(treatmentId));
    }

    @Override
    public BigDecimal consultationFee() {
        return consultationFee;
    }

    @Override
    public BigDecimal taxRatePercent() {
        return taxRatePercent;
    }

    @Override
    public BigDecimal followUpDiscountPercent() {
        return followUpDiscountPercent;
    }

    /**
     * Assembles an immutable price table.
     *
     * <p>This is a builder for one immutable value, not an application of the Builder design
     * pattern as a structural choice - the project implements exactly six patterns and this is not
     * one of them. It exists because a four-argument constructor plus a map literal reads far
     * worse at every call site.</p>
     */
    public static final class Builder {

        private final Map<Integer, TreatmentPrice> prices = new HashMap<>();
        private BigDecimal consultationFee = BigDecimal.ZERO;
        private BigDecimal taxRatePercent = BigDecimal.ZERO;
        private BigDecimal followUpDiscountPercent = BigDecimal.ZERO;

        /**
         * Adds one treatment to the table.
         *
         * @param id       the treatment id
         * @param code     the business code
         * @param name     the name for the receipt
         * @param baseCost the list price
         * @param taxable  whether the levy applies
         * @return this builder
         */
        public Builder treatment(int id, String code, String name, BigDecimal baseCost,
                                 boolean taxable) {
            prices.put(id, new TreatmentPrice(id, code, name, baseCost, taxable));
            return this;
        }

        public Builder consultationFee(BigDecimal fee) {
            this.consultationFee = fee;
            return this;
        }

        public Builder taxRatePercent(BigDecimal percent) {
            this.taxRatePercent = percent;
            return this;
        }

        public Builder followUpDiscountPercent(BigDecimal percent) {
            this.followUpDiscountPercent = percent;
            return this;
        }

        /** @return the assembled, immutable provider. */
        public InMemoryTreatmentPricingProvider build() {
            return new InMemoryTreatmentPricingProvider(this);
        }
    }
}
