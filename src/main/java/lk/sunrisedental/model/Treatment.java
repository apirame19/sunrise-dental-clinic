package lk.sunrisedental.model;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A treatment the clinic offers, and its price.
 *
 * <p>This is the single source of truth for what a procedure costs. No price is ever written into
 * Java code or a JSP; the billing engine reads it from here through the Bridge's pricing
 * provider.</p>
 *
 * <p>{@code durationMinutes} is what makes honest double-booking detection possible. A 90-minute
 * root canal starting at 10:00 must block a 10:30 booking, which a check comparing only start
 * times would miss entirely.</p>
 *
 * <p>{@code taxable} distinguishes preventive care (examinations, scaling), which is exempt from
 * the health service levy, from restorative and cosmetic work, which is not. That is what gives
 * the billing Decorator's tax component a real rule to apply rather than a flat percentage on
 * everything.</p>
 */
public class Treatment {

    private int treatmentId;
    private String treatmentCode;
    private String treatmentName;
    private String description;
    private BigDecimal baseCost;
    private int durationMinutes;
    private boolean taxable = true;
    private boolean active = true;

    public Treatment() {
    }

    public Treatment(int treatmentId, String treatmentCode, String treatmentName,
                     BigDecimal baseCost, int durationMinutes, boolean taxable) {
        this.treatmentId = treatmentId;
        this.treatmentCode = treatmentCode;
        this.treatmentName = treatmentName;
        this.baseCost = baseCost;
        this.durationMinutes = durationMinutes;
        this.taxable = taxable;
    }

    /**
     * @param startTime when the appointment begins
     * @return when it is expected to finish
     */
    public LocalTime endTimeFrom(LocalTime startTime) {
        return startTime == null ? null : startTime.plusMinutes(durationMinutes);
    }

    /** @return name and code, as shown in selection lists. */
    public String getDisplayName() {
        return treatmentName + " (" + treatmentCode + ")";
    }

    public int getTreatmentId() {
        return treatmentId;
    }

    public void setTreatmentId(int treatmentId) {
        this.treatmentId = treatmentId;
    }

    public String getTreatmentCode() {
        return treatmentCode;
    }

    public void setTreatmentCode(String treatmentCode) {
        this.treatmentCode = treatmentCode;
    }

    public String getTreatmentName() {
        return treatmentName;
    }

    public void setTreatmentName(String treatmentName) {
        this.treatmentName = treatmentName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBaseCost() {
        return baseCost;
    }

    public void setBaseCost(BigDecimal baseCost) {
        this.baseCost = baseCost;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public boolean isTaxable() {
        return taxable;
    }

    public void setTaxable(boolean taxable) {
        this.taxable = taxable;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Treatment treatment)) {
            return false;
        }
        return treatmentId != 0 && treatmentId == treatment.treatmentId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(treatmentId);
    }

    @Override
    public String toString() {
        return "Treatment{treatmentId=" + treatmentId
                + ", treatmentCode='" + treatmentCode + '\''
                + ", baseCost=" + baseCost
                + ", durationMinutes=" + durationMinutes
                + ", taxable=" + taxable + '}';
    }
}
