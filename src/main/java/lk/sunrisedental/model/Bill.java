package lk.sunrisedental.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An issued bill for one completed appointment.
 *
 * <p>Every monetary field is a {@link BigDecimal}. {@code double} is never used for money anywhere
 * in this system: {@code 0.1 + 0.2} is not {@code 0.3} in binary floating point, and a clinic that
 * came to this project because of billing errors is the last place to introduce silent rounding
 * drift.</p>
 *
 * <p>The stored amounts are a snapshot taken at the moment of billing, not a live calculation. If
 * the clinic raises the price of a root canal next month, a receipt already handed to a patient
 * must not change. That is why {@code bills} carries its own copies of the figures rather than
 * recomputing them from {@code treatments} on read.</p>
 *
 * <p>{@link #getLines()} holds the itemised breakdown produced by the billing decorators, in
 * receipt order.</p>
 */
public class Bill {

    private int billId;
    private String billNo;
    private int appointmentId;
    private String appointmentNo;

    private BigDecimal treatmentCost = BigDecimal.ZERO;
    private BigDecimal consultationFee = BigDecimal.ZERO;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;

    private final List<BillLine> lines = new ArrayList<>();

    private int generatedById;
    private String generatedByName;
    private LocalDateTime generatedAt;

    /** Populated for the printed receipt and the details screen. */
    private Appointment appointment;

    public Bill() {
    }

    /**
     * @return the sum of the itemised lines, which must equal {@link #getTotalAmount()}.
     *         Used by {@code BillingService} to assert internal consistency before the bill is
     *         written, mirroring the {@code SDC-202} database guard.
     */
    public BigDecimal getLineSum() {
        return lines.stream()
                .map(BillLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** @return {@code true} if the itemised lines add up to the recorded total. */
    public boolean isInternallyConsistent() {
        return totalAmount != null && getLineSum().compareTo(totalAmount) == 0;
    }

    /** @return the subtotal before tax, after any discount. */
    public BigDecimal getSubtotalBeforeTax() {
        return treatmentCost.add(consultationFee).subtract(discountAmount);
    }

    /** @return an unmodifiable view of the itemised lines, in receipt order. */
    public List<BillLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** Appends one itemised line. */
    public void addLine(BillLine line) {
        lines.add(Objects.requireNonNull(line, "line"));
    }

    /** Replaces all itemised lines. */
    public void setLines(List<BillLine> newLines) {
        lines.clear();
        if (newLines != null) {
            lines.addAll(newLines);
        }
    }

    public int getBillId() {
        return billId;
    }

    public void setBillId(int billId) {
        this.billId = billId;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public int getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(int appointmentId) {
        this.appointmentId = appointmentId;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }

    public void setAppointmentNo(String appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    public BigDecimal getTreatmentCost() {
        return treatmentCost;
    }

    public void setTreatmentCost(BigDecimal treatmentCost) {
        this.treatmentCost = treatmentCost;
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(BigDecimal consultationFee) {
        this.consultationFee = consultationFee;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public int getGeneratedById() {
        return generatedById;
    }

    public void setGeneratedById(int generatedById) {
        this.generatedById = generatedById;
    }

    public String getGeneratedByName() {
        return generatedByName;
    }

    public void setGeneratedByName(String generatedByName) {
        this.generatedByName = generatedByName;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Bill bill)) {
            return false;
        }
        return billNo != null && billNo.equals(bill.billNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(billNo);
    }

    @Override
    public String toString() {
        return "Bill{billNo='" + billNo + '\''
                + ", appointmentNo='" + appointmentNo + '\''
                + ", totalAmount=" + totalAmount + '}';
    }
}
