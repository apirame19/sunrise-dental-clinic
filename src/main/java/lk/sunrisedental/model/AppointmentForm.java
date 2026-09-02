package lk.sunrisedental.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The raw appointment-registration input, exactly as it arrived from the browser or the REST API.
 *
 * <p>Every field the user types is held as a {@code String}, including the date and time. That is
 * deliberate: a form cannot be modelled with a {@link LocalDate} field, because "31/02/2026" has to
 * survive long enough to be reported back to the user as an error, and a typed field cannot hold
 * it. Parsing is a validation step, not a precondition of validation.</p>
 *
 * <p>As each validator in the chain accepts its field, it stores the parsed value alongside the
 * raw one. The service layer then works from the parsed values and never re-parses, which is what
 * stops a value being interpreted one way during validation and another way during persistence.</p>
 *
 * <p>This object is also what repopulates the form when validation fails, so the receptionist does
 * not lose eight fields of typing because one of them was wrong.</p>
 */
public class AppointmentForm {

    // ---- raw input, as submitted -------------------------------------------------
    private String appointmentNo;
    private String patientName;
    private String address;
    private String contactNumber;
    private String dentistId;
    private String treatmentId;
    private String appointmentDate;
    private String appointmentTime;
    private String notes;

    // ---- parsed values, populated by the validation chain -------------------------
    private Integer parsedDentistId;
    private Integer parsedTreatmentId;
    private LocalDate parsedDate;
    private LocalTime parsedTime;
    private String normalisedContactNumber;

    public AppointmentForm() {
    }

    /** Trims every raw field, turning blank input into null so validators test one condition. */
    public void normalise() {
        appointmentNo = trimToNull(appointmentNo);
        patientName = trimToNull(patientName);
        address = trimToNull(address);
        contactNumber = trimToNull(contactNumber);
        dentistId = trimToNull(dentistId);
        treatmentId = trimToNull(treatmentId);
        appointmentDate = trimToNull(appointmentDate);
        appointmentTime = trimToNull(appointmentTime);
        notes = trimToNull(notes);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }

    public void setAppointmentNo(String appointmentNo) {
        this.appointmentNo = appointmentNo;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getDentistId() {
        return dentistId;
    }

    public void setDentistId(String dentistId) {
        this.dentistId = dentistId;
    }

    public String getTreatmentId() {
        return treatmentId;
    }

    public void setTreatmentId(String treatmentId) {
        this.treatmentId = treatmentId;
    }

    public String getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(String appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public String getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(String appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getParsedDentistId() {
        return parsedDentistId;
    }

    public void setParsedDentistId(Integer parsedDentistId) {
        this.parsedDentistId = parsedDentistId;
    }

    public Integer getParsedTreatmentId() {
        return parsedTreatmentId;
    }

    public void setParsedTreatmentId(Integer parsedTreatmentId) {
        this.parsedTreatmentId = parsedTreatmentId;
    }

    public LocalDate getParsedDate() {
        return parsedDate;
    }

    public void setParsedDate(LocalDate parsedDate) {
        this.parsedDate = parsedDate;
    }

    public LocalTime getParsedTime() {
        return parsedTime;
    }

    public void setParsedTime(LocalTime parsedTime) {
        this.parsedTime = parsedTime;
    }

    public String getNormalisedContactNumber() {
        return normalisedContactNumber;
    }

    public void setNormalisedContactNumber(String normalisedContactNumber) {
        this.normalisedContactNumber = normalisedContactNumber;
    }

    /** Personal data is not echoed into logs; only the appointment number identifies the form. */
    @Override
    public String toString() {
        return "AppointmentForm{appointmentNo='" + appointmentNo + '\''
                + ", date='" + appointmentDate + '\''
                + ", time='" + appointmentTime + '\'' + '}';
    }
}
