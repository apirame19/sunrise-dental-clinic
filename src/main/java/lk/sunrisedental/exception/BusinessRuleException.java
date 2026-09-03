package lk.sunrisedental.exception;

/**
 * A business rule refused the operation.
 *
 * <p>Used for the domain rules that do not warrant their own type: billing a cancelled
 * appointment, an illegal status transition, a bill that already exists, a discount larger than
 * the charges it applies to.</p>
 *
 * <p>Also the landing type for a rule enforced by a database trigger. The triggers signal
 * {@code SQLSTATE 45000} with a message prefixed {@code SDC-nnn}; the DAO layer matches on that
 * code and rethrows it as one of these with wording written for a receptionist rather than for a
 * database administrator. That is how a trigger stays a genuine last line of defence without ever
 * showing a raw SQL error to a user.</p>
 */
public class BusinessRuleException extends ClinicException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super("BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessRuleException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
