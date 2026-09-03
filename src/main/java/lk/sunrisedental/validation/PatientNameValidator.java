package lk.sunrisedental.validation;

import lk.sunrisedental.model.AppointmentForm;

import java.util.regex.Pattern;

/**
 * CHAIN OF RESPONSIBILITY handler - the patient's name.
 *
 * <p>The allowed character set is letters, spaces, apostrophes, hyphens and full stops. That is
 * wide enough for the names this clinic actually sees - "Roshan De Silva", "O'Brien",
 * "Anne-Marie", "Dr. Fernando" - and narrow enough to exclude digits and the punctuation that
 * appears in injection and scripting payloads.</p>
 *
 * <p>Note that this is a data-quality rule, not the system's defence against those payloads.
 * Injection is prevented by parameter binding in the DAO layer and scripting by escaping on
 * output; both hold regardless of what this handler allows. Validating input as well is defence in
 * depth, not the defence itself.</p>
 *
 * <p>Unicode letters are matched rather than {@code A-Za-z}, so a name written in Sinhala or Tamil
 * is accepted. A clinic in Colombo that rejected its patients' own scripts would be unusable.</p>
 */
public class PatientNameValidator extends Validator {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 100;

    /** \p{L} is any Unicode letter; \p{M} allows combining marks used in Sinhala and Tamil. */
    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{M} .'-]+$");

    @Override
    protected void doValidate(AppointmentForm form, ValidationResult result) {
        String value = form.getPatientName();

        if (value == null || value.isBlank()) {
            result.addError(FormFields.PATIENT_NAME, "Patient name is required.");
            return;
        }

        String trimmed = value.trim();
        form.setPatientName(trimmed);

        if (trimmed.length() < MIN_LENGTH) {
            result.addError(FormFields.PATIENT_NAME,
                    "Patient name must be at least " + MIN_LENGTH + " characters.");
            return;
        }
        if (trimmed.length() > MAX_LENGTH) {
            result.addError(FormFields.PATIENT_NAME,
                    "Patient name must be no more than " + MAX_LENGTH + " characters.");
            return;
        }
        if (!ALLOWED.matcher(trimmed).matches()) {
            result.addError(FormFields.PATIENT_NAME,
                    "Patient name may contain only letters, spaces, apostrophes, hyphens and "
                    + "full stops.");
        }
    }
}
