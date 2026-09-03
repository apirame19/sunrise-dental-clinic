package lk.sunrisedental.exception;

/**
 * No appointment exists for the number searched for.
 *
 * <p>The brief asks explicitly that a failed search be handled clearly, so this is a normal,
 * expected outcome of a search rather than an error condition. Controllers turn it into a plain
 * on-page message or an HTTP 404, never a stack trace.</p>
 */
public class AppointmentNotFoundException extends ClinicException {

    private static final long serialVersionUID = 1L;

    private final String appointmentNo;

    public AppointmentNotFoundException(String appointmentNo) {
        super("APPOINTMENT_NOT_FOUND",
              "No appointment was found with the number " + appointmentNo
              + ". Please check the number and try again.");
        this.appointmentNo = appointmentNo;
    }

    public String getAppointmentNo() {
        return appointmentNo;
    }
}
