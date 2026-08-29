package lk.sunrisedental.exception;

/**
 * Sign-in was refused.
 *
 * <p>The message is intentionally identical whether the username does not exist, the password is
 * wrong, or the account is deactivated. Distinguishing them would let anyone confirm which staff
 * usernames are valid, turning the login form into a directory of the clinic's employees.</p>
 *
 * <p>{@code errorCode} does distinguish the cases, because the server-side audit log needs to know
 * what actually happened. It is never sent to the browser.</p>
 */
public class AuthenticationException extends ClinicException {

    private static final long serialVersionUID = 1L;

    /** The single message shown for every kind of sign-in failure. */
    public static final String GENERIC_MESSAGE = "Invalid username or password.";

    public AuthenticationException(String errorCode) {
        super(errorCode, GENERIC_MESSAGE);
    }

    /** @return a locked-account failure, which is safe to describe because it needs staff action. */
    public static AuthenticationException accountLocked(int minutesRemaining) {
        return new AuthenticationException("ACCOUNT_LOCKED",
                "This account is temporarily locked after repeated failed sign-in attempts. "
                + "Please try again in " + minutesRemaining + " minute"
                + (minutesRemaining == 1 ? "" : "s") + ".");
    }

    private AuthenticationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /** @return a failure whose cause must not be disclosed to the browser. */
    public static AuthenticationException invalidCredentials(String auditReason) {
        return new AuthenticationException(auditReason);
    }
}
