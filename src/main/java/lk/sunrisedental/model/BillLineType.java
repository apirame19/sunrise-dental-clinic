package lk.sunrisedental.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The kind of charge a bill line represents.
 *
 * <p>These values correspond one-to-one with the billing Decorator chain: the base charge produces
 * a {@link #TREATMENT} line and each decorator contributes its own. Storing the type alongside the
 * amount is what lets the printed receipt group and label lines correctly, and lets the revenue
 * report separate treatment income from tax collected on the clinic's behalf.</p>
 */
public enum BillLineType {

    /** The treatment's own cost, taken from the price list. */
    TREATMENT("Treatment", false),

    /** The clinic's consultation fee, added once per bill. */
    CONSULTATION("Consultation", false),

    /** A reduction. Stored as a negative amount so the lines sum to the bill total. */
    DISCOUNT("Discount", true),

    /** Health service levy on taxable treatments. */
    TAX("Tax", false);

    private final String label;
    private final boolean deduction;

    BillLineType(String label, boolean deduction) {
        this.label = label;
        this.deduction = deduction;
    }

    /** @return a human-readable label for the receipt. */
    public String getLabel() {
        return label;
    }

    /** @return {@code true} if lines of this type reduce the total rather than adding to it. */
    public boolean isDeduction() {
        return deduction;
    }

    /**
     * @param value the candidate name, in any case, possibly padded or null
     * @return the matching type, or empty if not recognised
     */
    public static Optional<BillLineType> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalised = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalised))
                .findFirst();
    }
}
