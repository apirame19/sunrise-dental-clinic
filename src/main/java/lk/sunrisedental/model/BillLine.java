package lk.sunrisedental.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One itemised line on a bill.
 *
 * <p>Immutable. A line is produced by a billing decorator and then persisted; nothing in the
 * system has any business changing one afterwards, and making that impossible is cheaper than
 * remembering not to.</p>
 *
 * <p>{@code amount} is signed - a {@link BillLineType#DISCOUNT} line carries a negative value - so
 * that the lines on a receipt always sum to its total. A receipt whose lines do not add up is
 * exactly the billing error the clinic asked to be rid of.</p>
 */
public final class BillLine {

    private final int lineNo;
    private final BillLineType lineType;
    private final String description;
    private final BigDecimal amount;

    /**
     * @param lineNo      position on the receipt, starting at 1
     * @param lineType    the kind of charge
     * @param description the text shown to the patient
     * @param amount      signed amount; negative for a deduction
     */
    public BillLine(int lineNo, BillLineType lineType, String description, BigDecimal amount) {
        this.lineNo = lineNo;
        this.lineType = Objects.requireNonNull(lineType, "lineType");
        this.description = Objects.requireNonNull(description, "description");
        this.amount = Objects.requireNonNull(amount, "amount");
    }

    public int getLineNo() {
        return lineNo;
    }

    public BillLineType getLineType() {
        return lineType;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** @return the amount without its sign, for display next to a "less" label. */
    public BigDecimal getAbsoluteAmount() {
        return amount.abs();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BillLine line)) {
            return false;
        }
        return lineNo == line.lineNo
                && lineType == line.lineType
                && description.equals(line.description)
                && amount.compareTo(line.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lineNo, lineType, description, amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "BillLine{" + lineNo + ", " + lineType + ", '" + description + "', " + amount + '}';
    }
}
