package lk.sunrisedental.validation;

import lk.sunrisedental.model.AppointmentForm;

/**
 * CHAIN OF RESPONSIBILITY handler - the patient's address.
 *
 * <p>Only length is checked. Addresses are genuinely free-form - "15/3 Temple Road, Colombo 06"
 * contains a slash, a comma and digits - so any character-set rule strict enough to be useful
 * would reject real addresses. The five-character minimum exists to catch the placeholder entries
 * ("x", "n/a") that make a record useless later, and matches the CHECK constraint on the column so
 * the two cannot disagree.</p>
 */
public class AddressValidator extends Validator {

    private static final int MIN_LENGTH = 5;
    private static final int MAX_LENGTH = 255;

    @Override
    protected void doValidate(AppointmentForm form, ValidationResult result) {
        String value = form.getAddress();

        if (value == null || value.isBlank()) {
            result.addError(FormFields.ADDRESS, "Address is required.");
            return;
        }

        String trimmed = value.trim();
        form.setAddress(trimmed);

        if (trimmed.length() < MIN_LENGTH) {
            result.addError(FormFields.ADDRESS,
                    "Address must be at least " + MIN_LENGTH + " characters.");
            return;
        }
        if (trimmed.length() > MAX_LENGTH) {
            result.addError(FormFields.ADDRESS,
                    "Address must be no more than " + MAX_LENGTH + " characters.");
        }
    }
}
