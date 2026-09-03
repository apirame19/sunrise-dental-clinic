package lk.sunrisedental.exception;

/**
 * The appointment number submitted is already in use.
 *
 * <p>The brief requires every visit to carry a unique appointment number, so this is a first-class
 * failure rather than a generic constraint violation. It is distinct from
 * {@link DoubleBookingException}: that one means the dentist is busy, this one means the
 * identifier is taken.</p>
 */
public class DuplicateAppointmentException extends ClinicException {

    private static final long serialVersionUID = 1L;

    private final String appointmentNo;

    public DuplicateAppointmentException(String appointmentNo) {
        super("DUPLICATE_APPOINTMENT",
              "Appointment number " + appointmentNo + " is already in use. "
              + "Please use a different number.");
        this.appointmentNo = appointmentNo;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }
}
