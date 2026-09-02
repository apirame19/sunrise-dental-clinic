package lk.sunrisedental.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The lifecycle of an appointment.
 *
 * <p>The permitted transitions live here rather than being scattered through the service layer,
 * because they are a property of the domain and not of any one operation:</p>
 *
 * <pre>
 *   SCHEDULED ──► COMPLETED
 *             ──► CANCELLED
 *             ──► NO_SHOW
 *
 *   COMPLETED / CANCELLED / NO_SHOW ──► (nothing - terminal)
 * </pre>
 *
 * <p>Terminal states are final. Without that rule a cancelled appointment could be flipped to
 * {@code COMPLETED} and then billed, and the revenue reports would show income for a visit that
 * never took place.</p>
 *
 * <p>The same rule is enforced independently by {@code trg_appointments_before_update}
 * (error {@code SDC-104}). This enum is the friendly check that produces a readable message; the
 * trigger is the guarantee that holds even if the data is reached some other way.</p>
 */
public enum AppointmentStatus {

    /** Booked and expected. The only non-terminal state. */
    SCHEDULED("Scheduled"),

    /** The patient attended and was treated. Only this state may be billed. */
    COMPLETED("Completed"),

    /** Called off in advance. Frees the dentist's slot and cannot be billed. */
    CANCELLED("Cancelled"),

    /** The patient did not attend and did not cancel. Retains the slot for reporting. */
    NO_SHOW("No show");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    /** @return a human-readable label for display in the user interface. */
    public String getLabel() {
        return label;
    }

    /** @return {@code true} if no further status change is permitted from this state. */
    public boolean isTerminal() {
        return this != SCHEDULED;
    }

    /** @return the states this status may legally move to; empty for a terminal state. */
    public Set<AppointmentStatus> allowedTransitions() {
        return this == SCHEDULED
                ? EnumSet.of(COMPLETED, CANCELLED, NO_SHOW)
                : EnumSet.noneOf(AppointmentStatus.class);
    }

    /**
     * @param target the status being moved to
     * @return {@code true} if this appointment may legally become {@code target}
     */
    public boolean canTransitionTo(AppointmentStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    /** @return {@code true} if an appointment in this state may be billed. */
    public boolean isBillable() {
        return this == COMPLETED;
    }

    /** @return {@code true} if this state still occupies the dentist's slot. */
    public boolean occupiesSlot() {
        return this != CANCELLED;
    }

    /**
     * Parses a status from user or API input without throwing.
     *
     * @param value the candidate name, in any case, possibly padded or null
     * @return the matching status, or empty if the value is not recognised
     */
    public static Optional<AppointmentStatus> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalised = value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return Arrays.stream(values())
                .filter(status -> status.name().equals(normalised))
                .findFirst();
    }
}
