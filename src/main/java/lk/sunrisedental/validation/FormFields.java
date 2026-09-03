package lk.sunrisedental.validation;

/**
 * The form field names shared by the HTML inputs, the validators and the JSON API.
 *
 * <p>Named once here because the same string has to appear in three places for an error message to
 * land beside the input that caused it: the {@code name} attribute of the HTML control, the key a
 * validator records its error under, and the key the JSON {@code fieldErrors} object uses. A typo
 * in any one of them produces an error that is reported but never displayed - the worst kind,
 * because the form simply appears to reject valid input for no stated reason.</p>
 */
public final class FormFields {

    public static final String APPOINTMENT_NO   = "appointmentNo";
    public static final String PATIENT_NAME     = "patientName";
    public static final String ADDRESS          = "address";
    public static final String CONTACT_NUMBER   = "contactNumber";
    public static final String DENTIST_ID       = "dentistId";
    public static final String TREATMENT_ID     = "treatmentId";
    public static final String APPOINTMENT_DATE = "appointmentDate";
    public static final String APPOINTMENT_TIME = "appointmentTime";
    public static final String NOTES            = "notes";

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    private FormFields() {
    }
}
