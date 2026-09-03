package lk.sunrisedental.validation;

import lk.sunrisedental.model.AppointmentForm;

import java.util.regex.Pattern;

/**
 * CHAIN OF RESPONSIBILITY handler - the patient's contact number.
 *
 * <p>Accepts Sri Lankan numbers in either the local form {@code 0771234567} or the international
 * form {@code +94771234567}, and tolerates the spaces and hyphens people naturally type.</p>
 *
 * <p><strong>Normalisation is the point of this handler, not a side effect.</strong> A patient is
 * identified by the pair (name, contact number), so "077 123 4567" and "0771234567" must resolve
 * to the same person. Without normalising to a single canonical form before that lookup, the same
 * patient would be registered twice - and duplicate patient records were one of the four problems
 * the clinic asked this system to solve. The canonical value is stored on the form for the service
 * layer to use.</p>
 */
public class ContactNumberValidator extends Validator {

    /** Local: 0 followed by nine digits. International: +94 followed by nine digits. */
    private static final Pattern SRI_LANKAN = Pattern.compile("^(?:0\\d{9}|\\+94\\d{9})$");

    @Override
    protected void doValidate(AppointmentForm form, ValidationResult result) {
        String value = form.getContactNumber();

        if (value == null || value.isBlank()) {
            result.addError(FormFields.CONTACT_NUMBER, "Contact number is required.");
            return;
        }

        String trimmed = value.trim();
        form.setContactNumber(trimmed);

        // Strip only the separators people type; anything else left behind will fail the pattern.
        String compact = trimmed.replaceAll("[\\s-]", "");

        if (!SRI_LANKAN.matcher(compact).matches()) {
            result.addError(FormFields.CONTACT_NUMBER,
                    "Enter a valid Sri Lankan contact number, for example 0771234567 or "
                    + "+94771234567.");
            return;
        }

        form.setNormalisedContactNumber(toLocalForm(compact));
    }

    /**
     * Reduces both accepted forms to one.
     *
     * <p>{@code +94771234567} and {@code 0771234567} are the same telephone, and storing them
     * differently would split one patient's history across two records.</p>
     */
    private static String toLocalForm(String compact) {
        return compact.startsWith("+94") ? "0" + compact.substring(3) : compact;
    }
}
