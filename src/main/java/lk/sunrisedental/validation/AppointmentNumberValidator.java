package lk.sunrisedental.validation;

import lk.sunrisedental.model.AppointmentForm;

import java.util.regex.Pattern;

/**
 * CHAIN OF RESPONSIBILITY handler - the appointment number.
 *
 * <p>The brief makes the appointment number the key by which a visit is found, so it has to be
 * something a receptionist can read back over the telephone without ambiguity. Letters, digits and
 * hyphens only: spaces and punctuation invite transcription errors, and restricting the character
 * set also removes any question of what a quote or angle bracket would do further down.</p>
 *
 * <p>Uniqueness is not checked here. That requires a database round trip and belongs in
 * {@code AppointmentService}, which can also handle the race in which two receptionists submit the
 * same number at once. This handler answers only "is this a well-formed number".</p>
 */
public class AppointmentNumberValidator extends Validator {

    /** Matches the VARCHAR(20) column and the CHECK constraint on it. */
    private static final int MIN_LENGTH = 5;
    private static final int MAX_LENGTH = 20;

    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9-]+$");

    @Override
    protected void doValidate(AppointmentForm form, ValidationResult result) {
        String value = form.getAppointmentNo();

        if (value == null || value.isBlank()) {
            result.addError(FormFields.APPOINTMENT_NO, "Appointment number is required.");
            return;
        }

        String trimmed = value.trim();
        form.setAppointmentNo(trimmed);

        if (trimmed.length() < MIN_LENGTH) {
            result.addError(FormFields.APPOINTMENT_NO,
                    "Appointment number must be at least " + MIN_LENGTH + " characters.");
            return;
        }
        if (trimmed.length() > MAX_LENGTH) {
            result.addError(FormFields.APPOINTMENT_NO,
                    "Appointment number must be no more than " + MAX_LENGTH + " characters.");
            return;
        }
        if (!ALLOWED.matcher(trimmed).matches()) {
            result.addError(FormFields.APPOINTMENT_NO,
                    "Appointment number may contain only letters, digits and hyphens.");
        }
    }
}
