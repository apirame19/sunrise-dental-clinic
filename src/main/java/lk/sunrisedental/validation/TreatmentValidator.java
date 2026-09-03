package lk.sunrisedental.validation;

import lk.sunrisedental.dao.TreatmentDAO;
import lk.sunrisedental.model.AppointmentForm;
import lk.sunrisedental.model.Treatment;

import java.util.Optional;

/**
 * CHAIN OF RESPONSIBILITY handler - the chosen treatment.
 *
 * <p>The treatment is not merely a label on the appointment. It determines the price that will
 * appear on the bill and the duration that decides whether the dentist is free. An unrecognised
 * treatment would therefore produce both an unbillable appointment and an availability check with
 * no duration to work from, so it has to be rejected here rather than discovered later.</p>
 */
public class TreatmentValidator extends Validator {

    private final TreatmentDAO treatmentDAO;

    /**
     * @param treatmentDAO used to confirm the treatment exists and is still offered
     */
    public TreatmentValidator(TreatmentDAO treatmentDAO) {
        this.treatmentDAO = treatmentDAO;
    }

    @Override
    protected void doValidate(AppointmentForm form, ValidationResult result) {
        String value = form.getTreatmentId();

        if (value == null || value.isBlank()) {
            result.addError(FormFields.TREATMENT_ID, "Please select a treatment type.");
            return;
        }

        int treatmentId;
        try {
            treatmentId = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            result.addError(FormFields.TREATMENT_ID, "Please select a treatment from the list.");
            return;
        }

        if (treatmentId <= 0) {
            result.addError(FormFields.TREATMENT_ID, "Please select a treatment type.");
            return;
        }

        Optional<Treatment> treatment = treatmentDAO.findById(treatmentId);
        if (treatment.isEmpty()) {
            result.addError(FormFields.TREATMENT_ID, "The selected treatment could not be found.");
            return;
        }
        if (!treatment.get().isActive()) {
            result.addError(FormFields.TREATMENT_ID,
                    treatment.get().getTreatmentName() + " is no longer offered. "
                    + "Please choose another treatment.");
            return;
        }

        form.setParsedTreatmentId(treatmentId);
    }
}
