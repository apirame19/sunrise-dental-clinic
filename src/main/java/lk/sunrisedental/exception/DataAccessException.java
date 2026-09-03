package lk.sunrisedental.exception;

/**
 * A database operation failed for a technical reason.
 *
 * <p>Wraps the underlying {@code SQLException} so that the service and controller layers never
 * import {@code java.sql}. That is what keeps the data-access tier genuinely replaceable, and it
 * is enforced as layer rule R1/R2 in the architecture.</p>
 *
 * <p>{@link #getUserMessage()} deliberately returns a fixed, generic sentence. A raw SQL error
 * discloses table names, column names and sometimes data values; showing one to a receptionist
 * would be both useless to them and a gift to an attacker. The real cause is preserved for the
 * server log.</p>
 */
public class DataAccessException extends ClinicException {

    private static final long serialVersionUID = 1L;

    private static final String USER_MESSAGE =
            "The system could not complete that operation because of a database problem. "
            + "Please try again, and contact the system administrator if the problem continues.";

    public DataAccessException(String message, Throwable cause) {
        super("DATA_ACCESS_ERROR", message, cause);
    }

    public DataAccessException(String message) {
        super("DATA_ACCESS_ERROR", message);
    }

    @Override
    public String getUserMessage() {
        return USER_MESSAGE;
    }
}
