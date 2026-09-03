package lk.sunrisedental.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money arithmetic for the whole application.
 *
 * <p>Every monetary value is a {@link BigDecimal} scaled to two places and rounded
 * {@link RoundingMode#HALF_UP}. {@code double} is never used for money: {@code 0.1 + 0.2} is not
 * {@code 0.3} in binary floating point, and a clinic that came to this project because of billing
 * errors is the last place to introduce arithmetic that is almost right.</p>
 *
 * <p>HALF_UP rather than Java's default HALF_EVEN because it is what people expect when they check
 * a receipt by hand: 3.015 becomes 3.02, not 3.02-or-3.01-depending-on-the-preceding-digit.</p>
 *
 * <p>Centralising this means the rounding rule is applied identically by the tax calculation, the
 * discount calculation and the report totals, so they cannot disagree by a cent.</p>
 */
public final class Money {

    /** All monetary values carry exactly two decimal places. */
    public static final int SCALE = 2;

    /** Rounding applied at every step. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Zero, already scaled, for use as an initial value. */
    public static final BigDecimal ZERO = scale(BigDecimal.ZERO);

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    /**
     * @param value the amount to normalise; null is treated as zero
     * @return the amount at two decimal places, rounded half up
     */
    public static BigDecimal scale(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(SCALE, ROUNDING)
                : value.setScale(SCALE, ROUNDING);
    }

    /**
     * @param amount  the base amount
     * @param percent the percentage to take, for example {@code 2.50}
     * @return the percentage of the amount, scaled and rounded
     */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        if (amount == null || percent == null || percent.signum() == 0) {
            return ZERO;
        }
        return scale(amount.multiply(percent).divide(HUNDRED, SCALE + 4, ROUNDING));
    }

    /**
     * Adds any number of amounts.
     *
     * @param values the amounts; nulls are skipped
     * @return the scaled sum
     */
    public static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null) {
                total = total.add(value);
            }
        }
        return scale(total);
    }

    /**
     * @param value the amount
     * @param limit the ceiling
     * @return {@code value}, reduced to {@code limit} if it exceeded it
     */
    public static BigDecimal cappedAt(BigDecimal value, BigDecimal limit) {
        BigDecimal scaled = scale(value);
        BigDecimal ceiling = scale(limit);
        return scaled.compareTo(ceiling) > 0 ? ceiling : scaled;
    }

    /**
     * @param value the amount
     * @return the amount, or zero if it was negative. A bill total must never be negative.
     */
    public static BigDecimal notNegative(BigDecimal value) {
        BigDecimal scaled = scale(value);
        return scaled.signum() < 0 ? ZERO : scaled;
    }

    /**
     * Formats an amount for display.
     *
     * @param value    the amount
     * @param currency the ISO currency code, for example {@code LKR}
     * @return for example {@code LKR 24,087.50}
     */
    public static String format(BigDecimal value, String currency) {
        BigDecimal scaled = scale(value);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.00");
        return currency + " " + format.format(scaled);
    }
}
