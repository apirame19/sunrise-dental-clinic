package lk.sunrisedental.service;

import lk.sunrisedental.dao.AppointmentDAO;
import lk.sunrisedental.dao.BillDAO;
import lk.sunrisedental.dao.PatientDAO;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AppointmentForm;
import lk.sunrisedental.model.AppointmentStatus;
import lk.sunrisedental.model.Bill;
import lk.sunrisedental.model.Patient;
import lk.sunrisedental.model.PatientHistory;
import lk.sunrisedental.util.Money;
import lk.sunrisedental.validation.ValidationChainBuilder;
import lk.sunrisedental.validation.ValidationResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Patient records and history.
 *
 * <p>{@link #getHistory(int)} is the direct replacement for the paper file that used to go
 * missing. It assembles every visit, its treatment, its outcome and its bill into one object, so
 * the view renders a complete record without issuing a query per row.</p>
 */
public class PatientService {

    private final PatientDAO patientDAO;
    private final AppointmentDAO appointmentDAO;
    private final BillDAO billDAO;
    private final ValidationChainBuilder validationChainBuilder;

    public PatientService(PatientDAO patientDAO, AppointmentDAO appointmentDAO, BillDAO billDAO,
                          ValidationChainBuilder validationChainBuilder) {
        this.patientDAO = patientDAO;
        this.appointmentDAO = appointmentDAO;
        this.billDAO = billDAO;
        this.validationChainBuilder = validationChainBuilder;
    }

    /**
     * Registers a patient who is not being booked in at the same time.
     *
     * <p>Runs the same three validation handlers the booking form uses, so a contact number is
     * normalised identically whichever screen it arrives through. That normalisation is what makes
     * the duplicate check below meaningful: without it, "077 123 4567" and "0771234567" would be
     * two different people with two half-histories.</p>
     *
     * @param name    the patient's name
     * @param address where they live
     * @param contact their telephone number, in any accepted format
     * @param email   optional email address
     * @return the stored patient, carrying its generated id
     * @throws ValidationException   if any detail is unacceptable; carries every field error
     * @throws BusinessRuleException if this person is already registered
     */
    public Patient register(String name, String address, String contact, String email) {
        AppointmentForm form = new AppointmentForm();
        form.setPatientName(name);
        form.setAddress(address);
        form.setContactNumber(contact);

        ValidationResult validation = validationChainBuilder.validatePatientDetails(form);

        String cleanEmail = trimToNull(email);
        if (cleanEmail != null && (cleanEmail.length() > 120
                || cleanEmail.indexOf('@') < 1
                || cleanEmail.indexOf('@') == cleanEmail.length() - 1)) {
            validation.addError("email", "Enter a valid email address, or leave this blank.");
        }

        if (validation.hasErrors()) {
            throw new ValidationException(validation);
        }

        String normalisedContact = form.getNormalisedContactNumber() == null
                ? form.getContactNumber()
                : form.getNormalisedContactNumber();

        // uk_patients_identity would refuse this anyway; checking first turns a constraint
        // violation into a sentence that tells the receptionist the patient already exists.
        Optional<Patient> existing =
                patientDAO.findByNameAndContact(form.getPatientName(), normalisedContact);
        if (existing.isPresent()) {
            throw new BusinessRuleException("DUPLICATE_PATIENT",
                    form.getPatientName() + " is already registered with that contact number "
                    + "(patient " + existing.get().getPatientId() + "). Search for them instead.");
        }

        Patient patient = new Patient(form.getPatientName(), form.getAddress(), normalisedContact);
        patient.setEmail(cleanEmail);
        patient.setPatientId(patientDAO.insert(patient));
        return patient;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * @param patientId the patient
     * @return the patient record
     * @throws BusinessRuleException if no such patient exists
     */
    public Patient findById(int patientId) {
        return patientDAO.findById(patientId)
                .orElseThrow(() -> new BusinessRuleException("PATIENT_NOT_FOUND",
                        "No patient record was found."));
    }

    /**
     * @param searchTerm part of a name or contact number
     * @return matching patients; empty when the term is blank, rather than returning everyone
     */
    public List<Patient> search(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return List.of();
        }
        return patientDAO.search(searchTerm.trim());
    }

    /** @return every registered patient, ordered by name. */
    public List<Patient> findAll() {
        return patientDAO.findAll();
    }

    /** @return the total number of registered patients. */
    public long count() {
        return patientDAO.count();
    }

    /**
     * @param name          the patient's name
     * @param contactNumber the normalised contact number
     * @return the existing record for that person, if they are already registered
     */
    public Optional<Patient> findExisting(String name, String contactNumber) {
        return patientDAO.findByNameAndContact(name, contactNumber);
    }

    /**
     * Updates a patient's contact details.
     *
     * <p>The name is not changeable here. It forms half of the patient's identity key, so
     * correcting a misspelling is a data-administration task rather than a routine edit; allowing
     * it casually would silently merge or split records.</p>
     *
     * @param patient the patient carrying the new address, number and email
     * @return the updated record
     */
    public Patient updateContactDetails(Patient patient) {
        Patient existing = findById(patient.getPatientId());

        // The same handlers the registration and booking screens use. An edit that skipped them
        // would be the one route by which an unnormalised number could enter the identity key.
        AppointmentForm form = new AppointmentForm();
        form.setPatientName(existing.getPatientName());
        form.setAddress(patient.getAddress());
        form.setContactNumber(patient.getContactNumber());

        ValidationResult validation = validationChainBuilder.validatePatientDetails(form);
        if (validation.hasErrors()) {
            throw new ValidationException(validation);
        }

        existing.setAddress(form.getAddress());
        existing.setContactNumber(form.getNormalisedContactNumber() == null
                ? form.getContactNumber()
                : form.getNormalisedContactNumber());
        existing.setEmail(trimToNull(patient.getEmail()));

        if (!patientDAO.update(existing)) {
            throw new BusinessRuleException("PATIENT_UPDATE_FAILED",
                    "The patient record could not be updated.");
        }
        return existing;
    }

    /**
     * Finds the patient record that a self-registered login belongs to.
     *
     * <p>This is the lookup the patient portal is built on. A patient's own pages are scoped by
     * the record found here and never by an id taken from the request, so a patient cannot read
     * somebody else's history by editing a URL.</p>
     *
     * @param userId the signed-in account
     * @return the patient record, or empty if this account has none
     */
    public Optional<Patient> findByUserId(int userId) {
        return patientDAO.findByUserId(userId);
    }

    /**
     * Assembles a patient's complete clinical and financial history.
     *
     * @param patientId the patient
     * @return every visit with its bill, plus the totals staff are usually asked for
     */
    public PatientHistory getHistory(int patientId) {
        Patient patient = findById(patientId);
        List<Appointment> appointments = appointmentDAO.findByPatientId(patientId);

        List<Bill> bills = new ArrayList<>();
        BigDecimal totalBilled = BigDecimal.ZERO;

        for (Appointment appointment : appointments) {
            if (appointment.isBilled()) {
                Optional<Bill> bill = billDAO.findByAppointmentId(appointment.getAppointmentId());
                if (bill.isPresent()) {
                    bill.get().setAppointmentNo(appointment.getAppointmentNo());
                    bills.add(bill.get());
                    totalBilled = totalBilled.add(bill.get().getTotalAmount());
                }
            }
        }

        long completed = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
        long cancelled = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();
        long noShows = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();
        long upcoming = appointments.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.SCHEDULED).count();

        return new PatientHistory(patient, appointments, bills, Money.scale(totalBilled),
                completed, cancelled, noShows, upcoming);
    }
}
