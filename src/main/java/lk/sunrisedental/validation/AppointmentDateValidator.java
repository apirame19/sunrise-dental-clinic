package lk.sunrisedental.validation;

import lk.sunrisedental.model.AppointmentForm;
import lk.sunrisedental.patterns.singleton.ConfigurationManager;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * CHAIN OF RESPONSIBILITY handler - the appointment date.
 *
 * <p>Four rules, in the order a receptionist would hit them: the date must parse, it must not be
 * in the past, the clinic must be open that day, and it must fall inside the booking horizon.</p>
 *
 * <p><strong>The clock is injected.</strong> "In the past" is meaningless without a definition of
 * now, and a validator that called {@link LocalDate#now()} could only be tested by choosing dates
 * relative to whenever the suite happened to run. Passing a {@link Clock} lets the tests fix
 * today at a known Monday and assert precisely.</p>
 *
 * <p>Note what is validated and what is not: this rejects a past <em>date</em>, while a time
 * earlier today is rejected by {@link AppointmentTimeValidator}, which is the handler that knows
 * the time. Splitting it that way means each error message names the field the user must actually
 * change.</p>
 */
public class AppointmentDateValidator extends Validator {

    private final ConfigurationManager configuration;
    private final Clock clock;

    /**
     * @param configuration supplies the closed weekday and the booking horizon
     * @param clock         defines "today"; injected so the rule is testable
     */
    public AppointmentDateValidator(ConfigurationManager configuration, Clock clock) {
        this.configuration = configuration;
        this.clock = clock;
    }

    @Override
    protected void doValidate(AppointmentForm form, ValidationResult result) {
        String value = form.getAppointmentDate();

        if (value == null || value.isBlank()) {
            result.addError(FormFields.APPOINTMENT_DATE, "Appointment date is required.");
            return;
        }

        LocalDate date;
        try {
            // Strict ISO parsing. This rejects 2026-02-31 rather than rolling it forward to
            // 3 March, which is what a lenient parser would do - and the patient would then
            // arrive on a day nobody expected them.
            date = LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            result.addError(FormFields.APPOINTMENT_DATE,
                    "Enter the appointment date in the format YYYY-MM-DD, for example 2026-08-15.");
            return;
        }

        LocalDate today = LocalDate.now(clock);

        if (date.isBefore(today)) {
            result.addError(FormFields.APPOINTMENT_DATE,
                    "Appointment date cannot be in the past.");
            return;
        }

        int closedWeekday = configuration.getClosedWeekday();
        if (date.getDayOfWeek().getValue() == closedWeekday) {
            result.addError(FormFields.APPOINTMENT_DATE,
                    "The clinic is closed on "
                    + DayOfWeek.of(closedWeekday).getDisplayName(
                            java.time.format.TextStyle.FULL, java.util.Locale.UK)
                    + "s. Please choose another day.");
            return;
        }

        int maxDaysAhead = configuration.getMaxBookingDaysAhead();
        if (date.isAfter(today.plusDays(maxDaysAhead))) {
            result.addError(FormFields.APPOINTMENT_DATE,
                    "Appointments can only be booked up to " + maxDaysAhead + " days in advance.");
            return;
        }

        form.setParsedDate(date);
    }
}
