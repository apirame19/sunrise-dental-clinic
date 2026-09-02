package lk.sunrisedental.service;

import lk.sunrisedental.dao.AppointmentDAO;
import lk.sunrisedental.dao.DentistDAO;
import lk.sunrisedental.dao.PatientDAO;
import lk.sunrisedental.dao.TransactionManager;
import lk.sunrisedental.dao.TreatmentDAO;
import lk.sunrisedental.exception.AppointmentNotFoundException;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.exception.DoubleBookingException;
import lk.sunrisedental.exception.DuplicateAppointmentException;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AppointmentStatus;
import lk.sunrisedental.model.AppointmentForm;
import lk.sunrisedental.model.Dentist;
import lk.sunrisedental.model.Patient;
import lk.sunrisedental.model.Treatment;
import lk.sunrisedental.model.User;
import lk.sunrisedental.validation.ValidationChainBuilder;
import lk.sunrisedental.validation.ValidationResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The appointment workflow: registration, search, and the visit lifecycle.
 *
 * <p>This is where the clinic's worst problem is actually solved. Double booking is prevented at
 * three levels, and all three are needed:</p>
 *
 * <ol>
 *   <li>An explicit availability check before inserting, which produces a message naming the
 *       conflicting appointment. This is the path essentially every user takes.</li>
 *   <li>The composite unique key {@code uk_appointments_dentist_slot}, which catches the case two
 *       receptionists submit the same slot simultaneously and both pass the check before either
 *       writes. No amount of checking can close that window; only the database can.</li>
 *   <li>{@code trg_appointments_before_insert}, which catches overlaps that do not share a start
 *       time - a 90-minute root canal at 10:00 against a booking at 10:30.</li>
 * </ol>
 *
 * <p>The whole of {@link #register} runs in one transaction, so a new patient is never left
 * on file without the appointment they were created for.</p>
 *
 * <p>No {@code jakarta.servlet} import appears here, which is what lets every rule below be tested
 * with mocked DAOs and no container.</p>
 */
public class AppointmentService {

    private final AppointmentDAO appointmentDAO;
    private final PatientDAO patientDAO;
    private final DentistDAO dentistDAO;
    private final TreatmentDAO treatmentDAO;
    private final ValidationChainBuilder validationChainBuilder;
    private final TransactionManager transactionManager;

    public AppointmentService(AppointmentDAO appointmentDAO,
                              PatientDAO patientDAO,
                              DentistDAO dentistDAO,
                              TreatmentDAO treatmentDAO,
                              ValidationChainBuilder validationChainBuilder,
                              TransactionManager transactionManager) {
        this.appointmentDAO = appointmentDAO;
        this.patientDAO = patientDAO;
        this.dentistDAO = dentistDAO;
        this.treatmentDAO = treatmentDAO;
        this.validationChainBuilder = validationChainBuilder;
        this.transactionManager = transactionManager;
    }

    /**
     * Registers a new appointment, creating the patient record if this is a new patient.
     *
     * @param form      the submitted details
     * @param createdBy the signed-in member of staff
     * @return the stored appointment, fully populated
     * @throws ValidationException           if any field is unacceptable; carries every field error
     * @throws DuplicateAppointmentException if the appointment number is already in use
     * @throws DoubleBookingException        if the dentist is not free for the slot
     */
    public Appointment register(AppointmentForm form, User createdBy) {
        ValidationResult validation = validationChainBuilder.validate(form);
        if (validation.hasErrors()) {
            throw new ValidationException(validation);
        }

        Treatment treatment = treatmentDAO.findById(form.getParsedTreatmentId())
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_TREATMENT",
                        "The selected treatment could not be found."));
        Dentist dentist = dentistDAO.findById(form.getParsedDentistId())
                .orElseThrow(() -> new BusinessRuleException("UNKNOWN_DENTIST",
                        "The selected dentist could not be found."));

        // Checked before opening the transaction so the common rejections do not hold a
        // connection or take locks while the user is told what to fix.
        if (appointmentDAO.existsByAppointmentNo(form.getAppointmentNo())) {
            throw new DuplicateAppointmentException(form.getAppointmentNo());
        }
        requireDentistFree(dentist, form, treatment, null);

        int appointmentId = transactionManager.execute(connection -> {
            // Re-checked inside the transaction: between the check above and this write, another
            // receptionist may have taken the number.
            if (appointmentDAO.existsByAppointmentNo(connection, form.getAppointmentNo())) {
                throw new DuplicateAppointmentException(form.getAppointmentNo());
            }

            Patient patient = resolvePatient(form, connection);

            Appointment appointment = new Appointment();
            appointment.setAppointmentNo(form.getAppointmentNo());
            appointment.setPatient(patient);
            appointment.setDentist(dentist);
            appointment.setTreatment(treatment);
            appointment.setAppointmentDate(form.getParsedDate());
            appointment.setAppointmentTime(form.getParsedTime());
            appointment.setStatus(AppointmentStatus.SCHEDULED);
            appointment.setNotes(form.getNotes());

            return appointmentDAO.insert(appointment, createdBy.getUserId(), connection);
        });

        return appointmentDAO.findById(appointmentId)
                .orElseThrow(() -> new BusinessRuleException("REGISTRATION_FAILED",
                        "The appointment was saved but could not be read back."));
    }

    /**
     * Reuses an existing patient record or creates one.
     *
     * <p>Matching on the normalised contact number is what stops the same person being registered
     * twice with "077 123 4567" and "0771234567", which would split their history across two
     * records - one of the four problems this system was commissioned to fix.</p>
     */
    private Patient resolvePatient(AppointmentForm form, java.sql.Connection connection) {
        String contact = form.getNormalisedContactNumber() == null
                ? form.getContactNumber()
                : form.getNormalisedContactNumber();

        Optional<Patient> existing =
                patientDAO.findByNameAndContact(connection, form.getPatientName(), contact);

        if (existing.isPresent()) {
            return existing.get();
        }

        Patient patient = new Patient(form.getPatientName(), form.getAddress(), contact);
        patient.setPatientId(patientDAO.insert(patient, connection));
        return patient;
    }

    /**
     * Refuses the booking if the dentist is not free.
     *
     * @param excludeAppointmentId an appointment to ignore, when rescheduling it; null otherwise
     */
    private void requireDentistFree(Dentist dentist, AppointmentForm form, Treatment treatment,
                                    Integer excludeAppointmentId) {
        boolean free = appointmentDAO.isDentistAvailable(
                dentist.getDentistId(), form.getParsedDate(), form.getParsedTime(),
                treatment.getDurationMinutes(), excludeAppointmentId);

        if (free) {
            return;
        }

        String conflict = appointmentDAO.findConflictingAppointmentNo(
                dentist.getDentistId(), form.getParsedDate(), form.getParsedTime(),
                treatment.getDurationMinutes(), excludeAppointmentId).orElse(null);

        String message = dentist.getDentistName() + " already has an appointment that overlaps "
                + form.getParsedTime() + " on " + form.getParsedDate()
                + (conflict == null ? "." : " (" + conflict + ").")
                + " Please choose another time or dentist.";

        throw new DoubleBookingException(message, conflict);
    }

    /**
     * Finds an appointment by its number.
     *
     * @param appointmentNo the number to search for
     * @return the appointment, fully populated
     * @throws AppointmentNotFoundException if no such appointment exists
     */
    public Appointment findByNumber(String appointmentNo) {
        String trimmed = appointmentNo == null ? "" : appointmentNo.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("EMPTY_SEARCH",
                    "Please enter an appointment number to search for.");
        }
        return appointmentDAO.findByAppointmentNo(trimmed)
                .orElseThrow(() -> new AppointmentNotFoundException(trimmed));
    }

    /**
     * @param appointmentNo the number to search for
     * @return the appointment if it exists, empty otherwise; for callers that treat absence as
     *         an ordinary outcome rather than an error
     */
    public Optional<Appointment> search(String appointmentNo) {
        if (appointmentNo == null || appointmentNo.isBlank()) {
            return Optional.empty();
        }
        return appointmentDAO.findByAppointmentNo(appointmentNo.trim());
    }

    /**
     * Moves an appointment to a new status.
     *
     * @param appointmentNo the appointment
     * @param newStatus     the status to move to
     * @param reason        why, required when cancelling
     * @return the updated appointment
     * @throws BusinessRuleException if the transition is not permitted
     */
    public Appointment updateStatus(String appointmentNo, AppointmentStatus newStatus,
                                    String reason) {
        Appointment appointment = findByNumber(appointmentNo);

        if (appointment.getStatus() == newStatus) {
            return appointment;
        }

        // The enum owns the lifecycle rules, and trg_appointments_before_update enforces the same
        // thing independently. This check exists so the user gets a sentence, not a SQL error.
        if (!appointment.getStatus().canTransitionTo(newStatus)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "An appointment that is already " + appointment.getStatus().getLabel().toLowerCase()
                    + " cannot be changed to " + newStatus.getLabel().toLowerCase() + ".");
        }

        if (newStatus == AppointmentStatus.CANCELLED && (reason == null || reason.isBlank())) {
            throw new BusinessRuleException("CANCEL_REASON_REQUIRED",
                    "Please give a reason for cancelling this appointment.");
        }

        appointmentDAO.updateStatus(appointment.getAppointmentId(), newStatus,
                newStatus == AppointmentStatus.CANCELLED ? reason : null);

        return appointmentDAO.findById(appointment.getAppointmentId()).orElse(appointment);
    }

    /**
     * @param date the day
     * @return every appointment that day, ordered by dentist then time
     */
    public List<Appointment> findByDate(LocalDate date) {
        return appointmentDAO.findByDate(date);
    }

    /**
     * @param from inclusive start
     * @param to   inclusive end
     * @return appointments in the range
     */
    public List<Appointment> findByDateRange(LocalDate from, LocalDate to) {
        return appointmentDAO.findByDateRange(from, to);
    }

    /**
     * @param patientId the patient
     * @return that patient's visits, most recent first
     */
    public List<Appointment> findByPatient(int patientId) {
        return appointmentDAO.findByPatientId(patientId);
    }

    /**
     * @param dentistId the dentist
     * @param date      the day
     * @return that dentist's schedule
     */
    public List<Appointment> findDentistSchedule(int dentistId, LocalDate date) {
        return appointmentDAO.findByDentistAndDate(dentistId, date);
    }

    /**
     * @param from inclusive start
     * @param to   inclusive end
     * @return counts per status, with every status present even when zero
     */
    public Map<AppointmentStatus, Long> countByStatus(LocalDate from, LocalDate to) {
        return appointmentDAO.countByStatus(from, to);
    }

    /**
     * @param asOf the instant to treat as now
     * @return appointments whose slot has passed but whose outcome has not been recorded
     */
    public List<Appointment> findAwaitingOutcome(LocalDateTime asOf) {
        return appointmentDAO.findOverdueScheduled(asOf);
    }

    /**
     * Suggests the next unused appointment number for a date.
     *
     * @param date the appointment date
     * @return a number of the form {@code APT-YYYYMMDD-NNN}
     */
    public String suggestAppointmentNo(LocalDate date) {
        return appointmentDAO.generateNextAppointmentNo(date);
    }

    /** @return the treatments that may currently be booked. */
    public List<Treatment> availableTreatments() {
        return treatmentDAO.findAllActive();
    }

    /** @return the dentists currently taking appointments. */
    public List<Dentist> availableDentists() {
        return dentistDAO.findAllActive();
    }
}
