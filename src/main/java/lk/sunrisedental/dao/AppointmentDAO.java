package lk.sunrisedental.dao;

import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AppointmentStatus;

import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data access for appointments - the busiest interface in the system.
 *
 * <p>{@link #isDentistAvailable} and {@link #findConflictingAppointmentNo} delegate to the stored
 * functions {@code fn_is_dentist_available} and {@code fn_conflicting_appointment_no}. The overlap
 * comparison lives in the database because that is the only place it can be evaluated atomically
 * against current data; pulling a dentist's whole day into Java to compare intervals would open a
 * window between the check and the insert in which another receptionist can book the same slot.</p>
 *
 * <p>The application still checks availability before inserting, even though the unique key and
 * the trigger would catch a clash. The check produces a helpful message naming the conflicting
 * appointment; the constraint produces a correct but unhelpful failure. Both are needed: the check
 * for the ninety-nine per cent case, the constraint for the simultaneous-submission race that no
 * amount of checking can close.</p>
 */
public interface AppointmentDAO {

    /**
     * @param appointmentNo the business key, for example {@code APT-20260815-001}
     * @return the fully populated appointment, or empty if no such number exists
     */
    Optional<Appointment> findByAppointmentNo(String appointmentNo);

    /**
     * @param appointmentId the primary key
     * @return the fully populated appointment, or empty if not found
     */
    Optional<Appointment> findById(int appointmentId);

    /**
     * @param appointmentNo the number to test
     * @return {@code true} if that appointment number is already taken
     */
    boolean existsByAppointmentNo(String appointmentNo);

    /** As {@link #existsByAppointmentNo}, on a caller-supplied transactional connection. */
    boolean existsByAppointmentNo(Connection connection, String appointmentNo);

    /**
     * @param date the day to list
     * @return every appointment that day, ordered by dentist then time
     */
    List<Appointment> findByDate(LocalDate date);

    /**
     * @param from inclusive start date
     * @param to   inclusive end date
     * @return every appointment in the range, ordered by date and time
     */
    List<Appointment> findByDateRange(LocalDate from, LocalDate to);

    /**
     * @param patientId the patient
     * @return that patient's whole history, most recent visit first
     */
    List<Appointment> findByPatientId(int patientId);

    /**
     * @param dentistId the dentist
     * @param date      the day
     * @return that dentist's schedule for the day, ordered by time
     */
    List<Appointment> findByDentistAndDate(int dentistId, LocalDate date);

    /**
     * @param date   the day
     * @param status the status to filter by
     * @return matching appointments, ordered by time
     */
    List<Appointment> findByDateAndStatus(LocalDate date, AppointmentStatus status);

    /**
     * Finds appointments still marked scheduled whose slot has already passed.
     *
     * <p>Feeds the reminder queue's "awaiting outcome" list: staff have not yet recorded whether
     * the patient attended, and an appointment left in limbo never reaches a bill or a report.</p>
     *
     * @param asOf the instant to treat as now, supplied rather than read from the clock so this
     *             is testable
     * @return overdue appointments, oldest first
     */
    List<Appointment> findOverdueScheduled(LocalDateTime asOf);

    /**
     * Inserts an appointment within an existing transaction.
     *
     * @param appointment the appointment; its patient, dentist and treatment ids must be set
     * @param createdBy   the user registering it
     * @param connection  the transactional connection
     * @return the generated appointment id
     */
    int insert(Appointment appointment, int createdBy, Connection connection);

    /**
     * Changes an appointment's status.
     *
     * @param appointmentId the appointment
     * @param status        the new status
     * @param cancelReason  why it was cancelled, or {@code null} for other transitions
     * @return {@code true} if a row was changed
     */
    boolean updateStatus(int appointmentId, AppointmentStatus status, String cancelReason);

    /**
     * Asks the database whether a dentist is free for a proposed slot.
     *
     * @param dentistId              the dentist
     * @param date                   the proposed day
     * @param time                   the proposed start time
     * @param durationMinutes        how long the treatment takes
     * @param excludeAppointmentId   an appointment to ignore when rescheduling it, or {@code null}
     * @return {@code true} if nothing overlaps
     */
    boolean isDentistAvailable(int dentistId, LocalDate date, LocalTime time,
                               int durationMinutes, Integer excludeAppointmentId);

    /**
     * @return the number of the appointment blocking the proposed slot, or empty if it is free
     */
    Optional<String> findConflictingAppointmentNo(int dentistId, LocalDate date, LocalTime time,
                                                  int durationMinutes, Integer excludeAppointmentId);

    /**
     * Asks the database for the next unused appointment number for a date.
     *
     * @param date the appointment date
     * @return a number of the form {@code APT-YYYYMMDD-NNN}
     */
    String generateNextAppointmentNo(LocalDate date);

    /**
     * @param from inclusive start date
     * @param to   inclusive end date
     * @return count of appointments per status in the range
     */
    Map<AppointmentStatus, Long> countByStatus(LocalDate from, LocalDate to);

    /**
     * @param date the day
     * @return how many appointments are booked that day, in any status
     */
    long countByDate(LocalDate date);
}
