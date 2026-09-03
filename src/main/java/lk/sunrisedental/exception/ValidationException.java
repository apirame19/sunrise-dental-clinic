package lk.sunrisedental.exception;

import lk.sunrisedental.validation.ValidationResult;

/**
 * Input was rejected by the validation chain.
 *
 * <p>Carries the whole {@link ValidationResult}, not just a message, so that the controller can
 * render every field error beside its own input. Reducing a multi-field failure to a single
 * sentence is what forces users into the fix-one-thing-resubmit loop this system is meant to
 * avoid.</p>
 */
public class ValidationException extends ClinicException {

    private static final long serialVersionUID = 1L;

    private final transient ValidationResult validationResult;

    public ValidationException(ValidationResult validationResult) {
        super("VALIDATION_FAILED", validationResult.getSummary());
        this.validationResult = validationResult;
    }

    /** @return the full set of field and global errors. */
    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
