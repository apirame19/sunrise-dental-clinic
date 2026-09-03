package lk.sunrisedental.validation;

import lk.sunrisedental.model.AppointmentForm;

/**
 * CHAIN OF RESPONSIBILITY - abstract handler.
 *
 * <p>Each concrete validator is responsible for exactly one field. The alternative, a single
 * {@code validate()} method covering all eight fields the brief requires, becomes a two-hundred
 * line method in which no individual rule can be tested in isolation and every change risks the
 * rules either side of it.</p>
 *
 * <p><strong>A deliberate departure from the textbook pattern.</strong> Classic Chain of
 * Responsibility stops at the first handler able to deal with the request. This chain does not
 * stop: every handler runs, appends anything it finds to the shared {@link ValidationResult}, and
 * passes the request on. The reason is the user, not the pattern. A receptionist who has mistyped
 * the phone number, chosen a past date and left the address blank should be told all three at
 * once. A short-circuiting chain would report one, and they would submit three times to discover
 * the rest.</p>
 *
 * <p>What is retained from the pattern, and what makes it the right choice here, is that the
 * sender does not know which handler will deal with any given field, handlers can be reordered or
 * removed without touching each other, and a new rule is added by writing one class and linking
 * it in.</p>
 *
 * <p>{@link #validate} is final: the traversal is the pattern's contract, and a subclass that
 * forgot to call the next handler would silently disable every rule after it.</p>
 */
public abstract class Validator {

    private Validator next;

    /**
     * Appends a handler to the end of this chain.
     *
     * @param nextValidator the handler to run after this one
     * @return {@code nextValidator}, so calls can be chained fluently
     */
    public Validator setNext(Validator nextValidator) {
        this.next = nextValidator;
        return nextValidator;
    }

    /** @return the next handler, or {@code null} if this is the last. */
    protected Validator getNext() {
        return next;
    }

    /**
     * Runs this handler, then every handler after it.
     *
     * @param form   the submission being checked
     * @param result collects the problems found; never null
     */
    public final void validate(AppointmentForm form, ValidationResult result) {
        doValidate(form, result);
        if (next != null) {
            next.validate(form, result);
        }
    }

    /**
     * Checks the one field this handler is responsible for.
     *
     * <p>Implementations record problems on {@code result} and return normally. They must not
     * throw for invalid input - invalid input is the expected case, not an exceptional one.</p>
     *
     * @param form   the submission being checked
     * @param result where to record any problem found
     */
    protected abstract void doValidate(AppointmentForm form, ValidationResult result);

    /** @return a short name for this handler, used in diagnostics. */
    public String getName() {
        return getClass().getSimpleName();
    }
}
