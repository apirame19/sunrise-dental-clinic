package lk.sunrisedental.exception;

/**
 * The chosen dentist is already occupied for all or part of the requested slot.
 *
 * <p>This is the clinic's headline problem made into a type. It carries the appointment number
 * that blocks the slot, because "Dr. Silva already has APT-20260815-004 at 10:00" is something a
 * receptionist can act on immediately, whereas "slot unavailable" sends them hunting.</p>
 *
 * <p>Raised in two places for two different reasons. {@code AppointmentService} raises it after an
 * explicit availability check, which is the path virtually every user takes. The DAO also raises
 * it when MySQL rejects an insert against {@code uk_appointments_dentist_slot} or the overlap
 * trigger, which is the path taken when two receptionists submit the same slot simultaneously and
 * both pass the check before either writes.</p>
 */
public class DoubleBookingException extends ClinicException {

    private static final long serialVersionUID = 1L;

    private final String conflictingAppointmentNo;

    public DoubleBookingException(String message, String conflictingAppointmentNo) {
        super("DOUBLE_BOOKING", message);
        this.conflictingAppointmentNo = conflictingAppointmentNo;
    }

    public DoubleBookingException(String message) {
        this(message, null);
    }

    /** @return the appointment number occupying the slot, or {@code null} if it is not known. */
    public String getConflictingAppointmentNo() {
        return conflictingAppointmentNo;
    }
}
