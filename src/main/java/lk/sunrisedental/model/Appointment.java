package lk.sunrisedental.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A booked patient visit - the central entity of the system.
 *
 * <p>Holds references to the full {@link Patient}, {@link Dentist} and {@link Treatment} objects
 * rather than bare foreign-key integers. Search results, the details screen, the printed bill and
 * every report all need the related names and the treatment price, and a bare id would force each
 * of them to issue its own follow-up query. The DAO populates the whole graph in one join through
 * {@code vw_appointment_details}.</p>
 *
 * <p>Date and time are kept as separate {@link LocalDate} and {@link LocalTime} values to mirror
 * the {@code DATE} and {@code TIME} columns, because the clinic reasons about them separately -
 * "who is in on Thursday" and "what is booked at 10:00" are different questions.
 * {@link #getStartDateTime()} joins them when a single instant is needed.</p>
 */
public class Appointment {

    private int appointmentId;
    private String appointmentNo;
    private Patient patient;
    private Dentist dentist;
    private Treatment treatment;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;
    private String notes;
    private String cancelReason;
    private int createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Set when the appointment has been billed; null otherwise. */
    private Integer billId;
    private String billNo;

    public Appointment() {
    }

    /** @return the appointment start as a single instant. */
    public LocalDateTime getStartDateTime() {
        if (appointmentDate == null || appointmentTime == null) {
            return null;
        }
        return LocalDateTime.of(appointmentDate, appointmentTime);
    }

    /** @return when the appointment is expected to finish, from the treatment's duration. */
    public LocalDateTime getEndDateTime() {
        LocalDateTime start = getStartDateTime();
        if (start == null || treatment == null) {
            return null;
        }
        return start.plusMinutes(treatment.getDurationMinutes());
    }

    /** @return the expected finish time on the appointment day. */
    public LocalTime getEndTime() {
        return treatment == null || appointmentTime == null
                ? null
                : treatment.endTimeFrom(appointmentTime);
    }

    /** @return {@code true} if a bill has already been issued for this visit. */
    public boolean isBilled() {
        return billId != null && billId > 0;
    }

    /**
     * @return {@code true} if this appointment may be billed now: it must have been completed and
     *         must not already carry a bill.
     */
    public boolean isBillable() {
        return status != null && status.isBillable() && !isBilled();
    }

    /**
     * @param now the current instant, passed in rather than read from the system clock so that
     *            this is testable without waiting for time to pass
     * @return {@code true} if this is still scheduled but its slot has already gone by, meaning
     *         staff have not yet recorded whether the patient attended
     */
    public boolean isOverdueForOutcome(LocalDateTime now) {
        LocalDateTime end = getEndDateTime();
        return status == AppointmentStatus.SCHEDULED && end != null && end.isBefore(now);
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

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Dentist getDentist() {
        return dentist;
    }

    public void setDentist(Dentist dentist) {
        this.dentist = dentist;
    }

    public Treatment getTreatment() {
        return treatment;
    }

    public void setTreatment(Treatment treatment) {
        this.treatment = treatment;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public LocalTime getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(LocalTime appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public int getCreatedById() {
        return createdById;
    }

    public void setCreatedById(int createdById) {
        this.createdById = createdById;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getBillId() {
        return billId;
    }

    public void setBillId(Integer billId) {
        this.billId = billId;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Appointment appointment)) {
            return false;
        }
        return appointmentNo != null && appointmentNo.equals(appointment.appointmentNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appointmentNo);
    }

    @Override
    public String toString() {
        return "Appointment{appointmentNo='" + appointmentNo + '\''
                + ", date=" + appointmentDate
                + ", time=" + appointmentTime
                + ", status=" + status + '}';
    }
}
