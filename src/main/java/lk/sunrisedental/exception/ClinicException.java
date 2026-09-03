package lk.sunrisedental.exception;

/**
 * Base type for every failure this application raises deliberately.
 *
 * <p>Each carries a stable {@code errorCode} in addition to its message. The code is what the
 * REST controllers put in the JSON error envelope and what the HTML controllers use to decide
 * which message to show. Matching on a code rather than on the text of a message means the
 * wording can be improved without breaking a caller.</p>
 *
 * <p>Unchecked by design. These represent conditions the service layer detects and the controller
 * layer reports; the layers in between have nothing useful to do with them, and forcing every
 * intermediate method to declare or wrap them would add noise without adding safety.</p>
 */
public abstract class ClinicException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    protected ClinicException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected ClinicException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** @return the stable machine-readable code for this failure. */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * @return the message that is safe to show to an end user. Subclasses that wrap an underlying
     *         technical fault override this so that internal detail never reaches the browser.
     */
    public String getUserMessage() {
        return getMessage();
    }
}
