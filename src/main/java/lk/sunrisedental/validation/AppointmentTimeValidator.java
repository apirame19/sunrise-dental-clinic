package lk.sunrisedental.validation;

import lk.sunrisedental.model.AppointmentForm;
import lk.sunrisedental.patterns.singleton.ConfigurationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * CHAIN OF RESPONSIBILITY handler - the appointment time.
 *
 * <p>Runs last, and is the one handler that reads a field belonging to another: if the date is
 * today, the time must still be ahead of now. That cross-field rule has to live somewhere, and it
 * belongs with the time because the time is the field the user has to change to fix it.</p>
 *
 * <p>The slot-boundary rule keeps the diary tidy. Appointments starting at arbitrary minutes
 * fragment a dentist's day into gaps too short to book, which is one of the ways a paper diary
 * produces long waiting times.</p>
 *
 * <p>Only the start time is checked against closing time here; whether the treatment's duration
 * still fits before closing depends on the treatment and is enforced by the service layer and by
 * {@code trg_appointments_before_insert}.</p>
 */
public class AppointmentTimeValidator extends Validator {

    private final ConfigurationManager configuration;
    private final Clock clock;

    /**
     * @param configuration supplies opening hours and the slot length
     * @param clock         defines "now" for the today-only rule; injected so the rule is testable
     */
    public AppointmentTimeValidator(ConfigurationManager configuration, Clock clock) {
        this.configuration = configuration;
        this.clock = clock;
    }

    @Override
    protected void doValidate(AppointmentForm form, ValidationResult result) {
        String value = form.getAppointmentTime();

        if (value == null || value.isBlank()) {
            result.addError(FormFields.APPOINTMENT_TIME, "Appointment time is required.");
            return;
        }

        LocalTime time;
        try {
            time = LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            result.addError(FormFields.APPOINTMENT_TIME,
                    "Enter the appointment time in 24-hour format, for example 14:30.");
            return;
        }

        LocalTime opening = configuration.getOpeningTime();
        LocalTime closing = configuration.getClosingTime();

        if (time.isBefore(opening) || !time.isBefore(closing)) {
            result.addError(FormFields.APPOINTMENT_TIME,
                    "Appointment time must be between " + opening.toString() + " and "
                    + closing.toString() + ".");
            return;
        }

        int slotMinutes = configuration.getSlotMinutes();
        if (time.getSecond() != 0 || time.getMinute() % slotMinutes != 0) {
            result.addError(FormFields.APPOINTMENT_TIME,
                    "Appointment time must fall on a " + slotMinutes + " minute boundary, "
                    + "for example 10:00 or 10:" + slotMinutes + ".");
            return;
        }

        // Cross-field rule: a slot earlier today has already gone.
        LocalDate date = form.getParsedDate();
        if (date != null && date.equals(LocalDate.now(clock))
                && LocalDateTime.of(date, time).isBefore(LocalDateTime.now(clock))) {
            result.addError(FormFields.APPOINTMENT_TIME,
                    "That time has already passed today. Please choose a later time.");
            return;
        }

        form.setParsedTime(time);
    }
}
