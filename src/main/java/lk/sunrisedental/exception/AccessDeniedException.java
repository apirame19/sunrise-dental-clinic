package lk.sunrisedental.exception;

/**
 * The signed-in user is not permitted to perform this operation.
 *
 * <p>Distinct from {@link AuthenticationException}, which means "we do not know who you are". This
 * means "we know exactly who you are, and the answer is no" - and the two must produce different
 * responses: an unauthenticated request is sent to the login page, whereas sending an authenticated
 * receptionist there for opening a revenue report would look like a broken session and invite them
 * to sign in repeatedly.</p>
 *
 * <p>The message names the action rather than the data, so refusing access never itself discloses
 * what the data contains.</p>
 */
public class AccessDeniedException extends ClinicException {

    private static final long serialVersionUID = 1L;

    private final String action;

    /**
     * @param action what was attempted, phrased for display, for example "view revenue reports"
     */
    public AccessDeniedException(String action) {
        super("NOT_AUTHORISED", "Your role does not permit you to " + action + ".");
        this.action = action;
    }

    /** @return the attempted action, for the server log. */
    public String getAction() {
        return action;
    }
}
