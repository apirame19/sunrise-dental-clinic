package lk.sunrisedental.validation;

import lk.sunrisedental.dao.DentistDAO;
import lk.sunrisedental.dao.TreatmentDAO;
import lk.sunrisedental.model.AppointmentForm;
import lk.sunrisedental.patterns.singleton.ConfigurationManager;

import java.time.Clock;

/**
 * Assembles the appointment validation chain.
 *
 * <p>The order below is deliberate and is the order of the fields on the form, so that the list of
 * errors a receptionist sees reads top to bottom down the screen rather than jumping about.</p>
 *
 * <p>One ordering constraint is functional rather than cosmetic: the date handler must run before
 * the time handler, because the time handler's "that time has already passed today" rule needs the
 * parsed date. Everything else could be reordered freely, which is exactly the flexibility Chain
 * of Responsibility is for.</p>
 *
 * <p>{@link #build()} returns a <strong>new</strong> chain on every call. Validators hold no
 * per-request state, but the chain links are mutable, and a single shared instance handed to
 * concurrent requests would be one refactor away from a race. Building eight small objects per
 * submission is not a cost worth optimising against that risk.</p>
 */
public class ValidationChainBuilder {

    private final ConfigurationManager configuration;
    private final Clock clock;
    private final DentistDAO dentistDAO;
    private final TreatmentDAO treatmentDAO;

    /**
     * @param configuration supplies opening hours, slot length and the booking horizon
     * @param clock         defines "now" for the date and time rules
     * @param dentistDAO    used to confirm the chosen dentist exists and is active
     * @param treatmentDAO  used to confirm the chosen treatment exists and is offered
     */
    public ValidationChainBuilder(ConfigurationManager configuration, Clock clock,
                                  DentistDAO dentistDAO, TreatmentDAO treatmentDAO) {
        this.configuration = configuration;
        this.clock = clock;
        this.dentistDAO = dentistDAO;
        this.treatmentDAO = treatmentDAO;
    }

    /**
     * @return the head of a freshly linked chain covering all eight required fields
     */
    public Validator build() {
        Validator head = new AppointmentNumberValidator();

        head.setNext(new PatientNameValidator())
            .setNext(new AddressValidator())
            .setNext(new ContactNumberValidator())
            .setNext(new DentistValidator(dentistDAO))
            .setNext(new TreatmentValidator(treatmentDAO))
            .setNext(new AppointmentDateValidator(configuration, clock))
            .setNext(new AppointmentTimeValidator(configuration, clock));

        return head;
    }

    /**
     * Assembles the shorter chain used when registering a patient on their own, rather than as
     * part of a booking.
     *
     * <p>The same three handlers, linked differently. This is the pattern paying for itself: the
     * patient screen and the booking screen apply byte-for-byte identical rules to the name,
     * address and contact number because they run the same objects, not because two sets of rules
     * were written to match. Normalising the contact number matters most - it is half of the
     * patient's identity key, and a patient registered as "077 123 4567" here and booked as
     * "0771234567" there would become two people.</p>
     *
     * @return the head of a chain covering the patient's own details only
     */
    public Validator buildPatientDetailsChain() {
        Validator head = new PatientNameValidator();

        head.setNext(new AddressValidator())
            .setNext(new ContactNumberValidator());

        return head;
    }

    /**
     * Convenience: builds a chain, runs it, and returns what it found.
     *
     * @param form the submission to check; trimmed in place before validation
     * @return the problems found, empty if the submission is acceptable
     */
    public ValidationResult validate(AppointmentForm form) {
        form.normalise();
        ValidationResult result = new ValidationResult();
        build().validate(form, result);
        return result;
    }

    /**
     * Convenience: runs the patient-details chain only.
     *
     * @param form the submission to check; only the name, address and contact number are read
     * @return the problems found, empty if the details are acceptable
     */
    public ValidationResult validatePatientDetails(AppointmentForm form) {
        form.normalise();
        ValidationResult result = new ValidationResult();
        buildPatientDetailsChain().validate(form, result);
        return result;
    }
}
