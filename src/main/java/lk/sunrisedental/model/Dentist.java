package lk.sunrisedental.model;

import java.util.Objects;

/**
 * A dentist practising at the clinic.
 *
 * <p>Dentists are deactivated rather than deleted. Their historical appointments and the bills
 * derived from them must survive a departure, so {@code active} is what removes someone from the
 * booking dropdown while leaving the record intact.</p>
 */
public class Dentist {

    private int dentistId;
    private String dentistName;
    private String specialization;
    private String licenseNo;
    private String contactNumber;
    private boolean active = true;

    public Dentist() {
    }

    public Dentist(int dentistId, String dentistName, String specialization) {
        this.dentistId = dentistId;
        this.dentistName = dentistName;
        this.specialization = specialization;
    }

    /** @return name and specialisation, as shown in selection lists. */
    public String getDisplayName() {
        return specialization == null || specialization.isBlank()
                ? dentistName
                : dentistName + " (" + specialization + ")";
    }

    public int getDentistId() {
        return dentistId;
    }

    public void setDentistId(int dentistId) {
        this.dentistId = dentistId;
    }

    public String getDentistName() {
        return dentistName;
    }

    public void setDentistName(String dentistName) {
        this.dentistName = dentistName;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public void setLicenseNo(String licenseNo) {
        this.licenseNo = licenseNo;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
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
        if (!(other instanceof Dentist dentist)) {
            return false;
        }
        return dentistId != 0 && dentistId == dentist.dentistId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dentistId);
    }

    @Override
    public String toString() {
        return "Dentist{dentistId=" + dentistId
                + ", dentistName='" + dentistName + '\''
                + ", specialization='" + specialization + '\''
                + ", active=" + active + '}';
    }
}
