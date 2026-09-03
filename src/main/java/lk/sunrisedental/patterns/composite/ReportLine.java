package lk.sunrisedental.patterns.composite;

import lk.sunrisedental.util.Money;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * COMPOSITE - a leaf.
 *
 * <p>One row of a report: an appointment on the daily report, a treatment on the revenue report,
 * a bill on the billing summary.</p>
 *
 * <p>Carries an open map of extra columns. A leaf on the daily report needs the patient, the
 * dentist and the status; a leaf on the revenue report needs counts and a gross figure. Fixing
 * those as typed fields would mean either a class per report or a class carrying every column any
 * report might want, most of them null. The recursive parts of the interface - title, total,
 * count - are typed, and only the report-specific columns are open.</p>
 */
public class ReportLine implements ReportComponent {

    private final String title;
    private final String subtitle;
    private final BigDecimal amount;
    private final Map<String, String> columns = new LinkedHashMap<>();
    private int depth;

    /**
     * @param title    the label for this row
     * @param subtitle a secondary descriptor, or null
     * @param amount   the monetary value of this row; zero if it has none
     */
    public ReportLine(String title, String subtitle, BigDecimal amount) {
        this.title = title;
        this.subtitle = subtitle;
        this.amount = Money.scale(amount);
    }

    /**
     * Adds a report-specific column.
     *
     * @param label the column heading
     * @param value the value, already formatted for display
     * @return this line, so columns can be added fluently while building a report
     */
    public ReportLine withColumn(String label, String value) {
        columns.put(label, value);
        return this;
    }

    /** @return the report-specific columns, in insertion order. */
    public Map<String, String> getColumns() {
        return Collections.unmodifiableMap(columns);
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getSubtitle() {
        return subtitle;
    }

    @Override
    public BigDecimal getTotal() {
        return amount;
    }

    /** @return one - a leaf is exactly one record. */
    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public List<ReportComponent> getChildren() {
        return List.of();
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public void setDepth(int depth) {
        this.depth = depth;
    }

    @Override
    public String toString() {
        return "ReportLine{" + title + ", " + amount + '}';
    }
}
