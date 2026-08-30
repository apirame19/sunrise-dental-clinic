package lk.sunrisedental.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A patient of the clinic.
 *
 * <p>Holds only what the brief requires: name, address and contact number, plus an optional email.
 * No national identity number and no date of birth, because no business rule needs them and a
 * clinical system should hold the minimum personal data that lets it do its job.</p>
 *
 * <p>Identity is the pair (name, contact number). A household commonly shares one telephone
 * number, so the number alone cannot identify a person; this is mirrored by
 * {@code uk_patients_identity} in the schema.</p>
 */
public class Patient {

    private int patientId;
    private String patientName;
    private String address;
    private String contactNumber;
    private String email;
    private LocalDateTime createdAt;

    public Patient() {
    }

    public Patient(String patientName, String address, String contactNumber) {
        this.patientName = patientName;
        this.address = address;
        this.contactNumber = contactNumber;
    }

    public Patient(int patientId, String patientName, String address, String contactNumber) {
        this(patientName, address, contactNumber);
        this.patientId = patientId;
    }

    /** @return {@code true} if this patient has not yet been persisted. */
    public boolean isNew() {
        return patientId == 0;
    }

    public int getPatientId() {
        return patientId;
    }

    public void setPatientId(int patientId) {
        this.patientId = patientId;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Patient patient)) {
            return false;
        }
        return patientId != 0 && patientId == patient.patientId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(patientId);
    }

    @Override
    public String toString() {
        return "Patient{patientId=" + patientId
                + ", patientName='" + patientName + '\''
                + ", contactNumber='" + contactNumber + '\'' + '}';
    }
}
