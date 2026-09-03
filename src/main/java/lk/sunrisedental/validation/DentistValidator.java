package lk.sunrisedental.validation;

import lk.sunrisedental.dao.DentistDAO;
import lk.sunrisedental.model.AppointmentForm;
import lk.sunrisedental.model.Dentist;

import java.util.Optional;

/**
 * CHAIN OF RESPONSIBILITY handler - the chosen dentist.
 *
 * <p>The brief describes capturing a "dentist name", but a free-text name is unusable: "Dr Silva",
 * "Dr. Silva" and "silva" would become three different dentists, and no per-dentist report could
 * ever be trusted. The form therefore submits a dentist id chosen from a list, and this handler
 * confirms it refers to a dentist who exists and is still practising.</p>
 *
 * <p>Checking the id server-side matters even though the browser offered a dropdown. The value
 * arriving here is whatever was posted, not whatever was displayed, and the REST endpoint accepts
 * the same field with no dropdown at all.</p>
 */
public class DentistValidator extends Validator {

    private final DentistDAO dentistDAO;

    /**
     * @param dentistDAO used to confirm the dentist exists and is active
     */
    public DentistValidator(DentistDAO dentistDAO) {
        this.dentistDAO = dentistDAO;
    }

    @Override
    protected void doValidate(AppointmentForm form, ValidationResult result) {
        String value = form.getDentistId();

        if (value == null || value.isBlank()) {
            result.addError(FormFields.DENTIST_ID, "Please select a dentist.");
            return;
        }

        int dentistId;
        try {
            dentistId = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            result.addError(FormFields.DENTIST_ID, "Please select a dentist from the list.");
            return;
        }

        if (dentistId <= 0) {
            result.addError(FormFields.DENTIST_ID, "Please select a dentist.");
            return;
        }

        Optional<Dentist> dentist = dentistDAO.findById(dentistId);
        if (dentist.isEmpty()) {
            result.addError(FormFields.DENTIST_ID, "The selected dentist could not be found.");
            return;
        }
        if (!dentist.get().isActive()) {
            result.addError(FormFields.DENTIST_ID,
                    "Dr. " + dentist.get().getDentistName() + " is no longer taking appointments. "
                    + "Please choose another dentist.");
            return;
        }

        form.setParsedDentistId(dentistId);
    }
}
