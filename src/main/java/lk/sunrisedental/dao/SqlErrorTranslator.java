package lk.sunrisedental.dao;

import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.exception.ClinicException;
import lk.sunrisedental.exception.DataAccessException;
import lk.sunrisedental.exception.DoubleBookingException;
import lk.sunrisedental.exception.DuplicateAppointmentException;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a {@link SQLException} into the domain exception it actually represents.
 *
 * <p>This class is what allows the database to be a genuine last line of defence without users
 * ever seeing a raw SQL error. The triggers deliberately signal {@code SQLSTATE 45000} with a
 * message prefixed {@code SDC-nnn}; matching on that code - rather than on English prose - means
 * the wording of a trigger message can be improved without breaking this mapping.</p>
 *
 * <p>Unique-key violations are handled the same way, by constraint name. This matters for a
 * specific and unavoidable race: two receptionists can both pass the application's availability
 * check for the same slot before either one writes. Whichever loses gets a constraint violation
 * from MySQL, and it must arrive at the user as "that dentist is already booked", not as
 * "Duplicate entry '3-2026-08-15-10:00:00' for key 'uk_appointments_dentist_slot'".</p>
 *
 * <p>Anything unrecognised becomes a {@link DataAccessException}, whose user-facing message is a
 * fixed generic sentence. Unrecognised database errors are exactly the ones most likely to
 * disclose schema detail, so they are the ones that must never be shown.</p>
 */
public final class SqlErrorTranslator {

    /** SQLSTATE raised by every SIGNAL in triggers.sql. */
    private static final String SQLSTATE_TRIGGER_SIGNAL = "45000";

    /** SQLSTATE class for integrity constraint violations. */
    private static final String SQLSTATE_INTEGRITY_VIOLATION = "23000";

    private static final Pattern SDC_CODE = Pattern.compile("SDC-(\\d{3})");

    /** Pulls the appointment number out of a trigger message such as "...(APT-20260815-004)." */
    private static final Pattern APPOINTMENT_NO = Pattern.compile("(APT-\\d{8}-\\d{3})");

    private SqlErrorTranslator() {
    }

    /**
     * Translates a database failure into the most specific domain exception available.
     *
     * @param e       the failure as reported by JDBC
     * @param context short description of what was being attempted, for the server log only
     * @return a domain exception; never null
     */
    public static ClinicException translate(SQLException e, String context) {
        String state = e.getSQLState();
        String message = e.getMessage() == null ? "" : e.getMessage();

        if (SQLSTATE_TRIGGER_SIGNAL.equals(state)) {
            return fromTriggerSignal(message, e);
        }
        if (SQLSTATE_INTEGRITY_VIOLATION.equals(state)) {
            return fromConstraintViolation(message, e);
        }
        return new DataAccessException(context + ": " + message, e);
    }

    /** Maps an {@code SDC-nnn} code signalled by a trigger. */
    private static ClinicException fromTriggerSignal(String message, SQLException cause) {
        Matcher matcher = SDC_CODE.matcher(message);
        String code = matcher.find() ? matcher.group(1) : "";

        return switch (code) {
            case "101" -> new BusinessRuleException("APPOINTMENT_IN_PAST",
                    "An appointment cannot be scheduled in the past. "
                    + "Please choose a future date and time.");

            case "102" -> new BusinessRuleException("OUTSIDE_CLINIC_HOURS",
                    "The appointment must start and finish within clinic opening hours.");

            case "103" -> new DoubleBookingException(
                    "The selected dentist already has an appointment that overlaps this time slot."
                    + conflictSuffix(message),
                    extractAppointmentNo(message));

            case "104" -> new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "This appointment has already been closed, so its status can no longer be "
                    + "changed.");

            case "105" -> new BusinessRuleException("NOT_RESCHEDULABLE",
                    "Only a scheduled appointment can be rescheduled.");

            case "201" -> new BusinessRuleException("CANCELLED_NOT_BILLABLE",
                    "A cancelled appointment cannot be billed.");

            case "202" -> new BusinessRuleException("BILL_TOTAL_MISMATCH",
                    "The bill could not be issued because its total does not match the sum of its "
                    + "charges. No bill has been saved.");

            case "203" -> new BusinessRuleException("BILL_IMMUTABLE",
                    "An issued bill cannot be altered. Please issue a corrected bill instead.");

            default -> new BusinessRuleException("BUSINESS_RULE_VIOLATION",
                    "That operation was refused because it would break a clinic business rule.",
                    cause);
        };
    }

    /** Maps a unique or foreign-key violation by constraint name. */
    private static ClinicException fromConstraintViolation(String message, SQLException cause) {
        String lower = message.toLowerCase();

        if (lower.contains("uk_appointments_no")) {
            return new DuplicateAppointmentException(extractDuplicateValue(message));
        }
        if (lower.contains("uk_appointments_dentist_slot")) {
            return new DoubleBookingException(
                    "The selected dentist was booked for this exact time slot by another user a "
                    + "moment ago. Please choose a different time.");
        }
        if (lower.contains("uk_bills_appointment")) {
            return new BusinessRuleException("BILL_ALREADY_EXISTS",
                    "A bill has already been issued for this appointment.");
        }
        if (lower.contains("uk_bills_no")) {
            return new BusinessRuleException("DUPLICATE_BILL_NUMBER",
                    "That bill number is already in use. Please try again.");
        }
        if (lower.contains("uk_patients_identity")) {
            return new BusinessRuleException("DUPLICATE_PATIENT",
                    "A patient with that name and contact number already exists.");
        }
        if (lower.contains("uk_users_username")) {
            return new BusinessRuleException("DUPLICATE_USERNAME",
                    "That username is already taken.");
        }
        if (lower.contains("foreign key")) {
            return new BusinessRuleException("REFERENCE_VIOLATION",
                    "That record refers to something that does not exist, or is still referred to "
                    + "by another record.");
        }
        return new DataAccessException("Constraint violation: " + message, cause);
    }

    /** @return " (APT-...)" when the trigger named the clashing appointment, otherwise "". */
    private static String conflictSuffix(String message) {
        String appointmentNo = extractAppointmentNo(message);
        return appointmentNo == null ? "" : " Conflicting appointment: " + appointmentNo + ".";
    }

    private static String extractAppointmentNo(String message) {
        Matcher matcher = APPOINTMENT_NO.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Pulls the offending value out of MySQL's "Duplicate entry 'X' for key '...'" text.
     *
     * @return the duplicated appointment number, or a neutral placeholder if it cannot be found
     */
    private static String extractDuplicateValue(String message) {
        Matcher matcher = Pattern.compile("Duplicate entry '([^']+)'").matcher(message);
        return matcher.find() ? matcher.group(1) : "that value";
    }
}
