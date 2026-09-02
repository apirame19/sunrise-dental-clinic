package lk.sunrisedental.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row returned by a reporting stored procedure.
 *
 * <p>The reporting procedures each return a different shape - a daily list, a per-dentist
 * aggregate, a revenue breakdown - so a fixed DTO per report would mean six near-identical classes
 * whose only job is to be populated once and read once. This carries the row generically instead,
 * with typed accessors so that callers still never cast.</p>
 *
 * <p>The accessors are null-tolerant by design. Reporting queries use {@code LEFT JOIN} heavily -
 * a dentist with no appointments in the period, a treatment never billed - and a report that threw
 * on the first empty cell would be useless for exactly the quiet periods a manager most wants to
 * see.</p>
 */
public final class ReportRow {

    private final Map<String, Object> values;

    public ReportRow(Map<String, Object> values) {
        this.values = new LinkedHashMap<>(values);
    }

    /**
     * @param column the column label
     * @return the value as text, or {@code null} if the column is absent or SQL NULL
     */
    public String getString(String column) {
        Object value = values.get(column);
        return value == null ? null : value.toString();
    }

    /**
     * @param column the column label
     * @return the value as text, or {@code fallback} if absent or SQL NULL
     */
    public String getString(String column, String fallback) {
        String value = getString(column);
        return value == null ? fallback : value;
    }

    /**
     * @param column the column label
     * @return the value as an int; zero if absent or SQL NULL
     */
    public int getInt(String column) {
        Object value = values.get(column);
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * @param column the column label
     * @return the value as a long; zero if absent or SQL NULL
     */
    public long getLong(String column) {
        Object value = values.get(column);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * @param column the column label
     * @return the value as a {@link BigDecimal}; {@link BigDecimal#ZERO} if absent or SQL NULL.
     *         Money is never returned as a {@code double}.
     */
    public BigDecimal getMoney(String column) {
        Object value = values.get(column);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return BigDecimal.ZERO;
    }

    /**
     * @param column the column label
     * @return the value as a date, or {@code null} if absent or SQL NULL
     */
    public LocalDate getDate(String column) {
        Object value = values.get(column);
        return value instanceof LocalDate date ? date : null;
    }

    /**
     * @param column the column label
     * @return the value as a time, or {@code null} if absent or SQL NULL
     */
    public LocalTime getTime(String column) {
        Object value = values.get(column);
        return value instanceof LocalTime time ? time : null;
    }

    /**
     * @param column the column label
     * @return {@code true} if the column is present with a non-null value
     */
    public boolean has(String column) {
        return values.get(column) != null;
    }

    /** @return every column in the row, unmodifiable; used when rendering a generic report table. */
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public String toString() {
        return "ReportRow" + values;
    }
}
