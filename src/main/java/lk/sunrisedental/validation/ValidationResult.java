package lk.sunrisedental.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Accumulates the problems found while validating a submission.
 *
 * <p>This is the object that travels along the validation chain. Each handler appends what it
 * finds and passes it on, which is why the receptionist sees every bad field at once instead of
 * fixing one, resubmitting, and being told about the next.</p>
 *
 * <p>Errors are keyed by field name so the HTML view can print each message beside the input it
 * belongs to, and the JSON API can return the same map as {@code fieldErrors}. One validation
 * implementation, two renderings.</p>
 *
 * <p>A {@link LinkedHashMap} preserves chain order, so the messages appear in the same order as
 * the fields on the form. Only the first error per field is kept: telling someone their phone
 * number is both too short and contains a letter is noise when the one instruction "enter a valid
 * Sri Lankan number" covers it.</p>
 */
public class ValidationResult {

    private final Map<String, String> fieldErrors = new LinkedHashMap<>();
    private final Set<String> globalErrors = new LinkedHashSet<>();

    /**
     * Records a problem against a specific input field.
     *
     * <p>Ignored if that field already has an error, so the first and most specific message wins.</p>
     *
     * @param field   the form field name, matching the HTML {@code name} attribute
     * @param message the message to show the user
     */
    public void addError(String field, String message) {
        if (field == null || message == null) {
            return;
        }
        fieldErrors.putIfAbsent(field, message);
    }

    /**
     * Records a problem that belongs to the submission as a whole rather than to one field,
     * such as a dentist being unavailable for the chosen date and time together.
     *
     * @param message the message to show the user
     */
    public void addGlobalError(String message) {
        if (message != null) {
            globalErrors.add(message);
        }
    }

    /** @return {@code true} if nothing was rejected. */
    public boolean isValid() {
        return fieldErrors.isEmpty() && globalErrors.isEmpty();
    }

    /** @return {@code true} if at least one problem was recorded. */
    public boolean hasErrors() {
        return !isValid();
    }

    /** @param field the form field name
     *  @return {@code true} if that field was rejected. */
    public boolean hasError(String field) {
        return fieldErrors.containsKey(field);
    }

    /** @param field the form field name
     *  @return the message for that field, or {@code null} if it was accepted. */
    public String getError(String field) {
        return fieldErrors.get(field);
    }

    /** @return field name to message, in chain order; unmodifiable. */
    public Map<String, String> getFieldErrors() {
        return Collections.unmodifiableMap(fieldErrors);
    }

    /** @return messages not tied to a single field; unmodifiable. */
    public Set<String> getGlobalErrors() {
        return Collections.unmodifiableSet(globalErrors);
    }

    /** @return the total number of problems recorded. */
    public int getErrorCount() {
        return fieldErrors.size() + globalErrors.size();
    }

    /** @return a one-line summary for the banner at the top of the form. */
    public String getSummary() {
        int count = getErrorCount();
        if (count == 0) {
            return "";
        }
        return count == 1
                ? "There is 1 problem with this form. Please correct it and try again."
                : "There are " + count + " problems with this form. Please correct them and try again.";
    }

    @Override
    public String toString() {
        return "ValidationResult{fieldErrors=" + fieldErrors + ", globalErrors=" + globalErrors + '}';
    }
}
