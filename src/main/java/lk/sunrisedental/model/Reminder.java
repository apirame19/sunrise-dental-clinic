package lk.sunrisedental.model;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One entry in the dashboard reminder queue.
 *
 * <p>Wraps the appointment it refers to rather than copying its fields, so the queue can show the
 * patient, dentist, treatment and time without a second lookup, and cannot drift out of step with
 * the appointment it describes.</p>
 *
 * <p>Immutable. A reminder is derived from current data every time the dashboard is loaded; there
 * is nothing to mutate.</p>
 */
public final class Reminder {

    private final Appointment appointment;
    private final ReminderType type;
    private final LocalDateTime generatedAt;

    /**
     * @param appointment the appointment being reminded about
     * @param type        why it is in the queue
     * @param generatedAt when the queue was built, used to describe how soon or how overdue
     */
    public Reminder(Appointment appointment, ReminderType type, LocalDateTime generatedAt) {
        this.appointment = appointment;
        this.type = type;
        this.generatedAt = generatedAt;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public ReminderType getType() {
        return type;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    /** @return {@code true} if the slot has passed with no outcome recorded. */
    public boolean isOverdue() {
        return type == ReminderType.AWAITING_OUTCOME;
    }

    /**
     * Describes the timing in words, because "in 3 hours" is easier to act on at a busy front desk
     * than a timestamp the reader has to subtract from the clock on the wall.
     *
     * @return for example "in 45 minutes", "tomorrow at 09:00", or "2 hours overdue"
     */
    public String getTimingDescription() {
        LocalDateTime start = appointment.getStartDateTime();
        if (start == null) {
            return "";
        }

        if (isOverdue()) {
            Duration late = Duration.between(appointment.getEndDateTime(), generatedAt);
            if (late.toHours() >= 24) {
                long days = late.toDays();
                return days + (days == 1 ? " day overdue" : " days overdue");
            }
            if (late.toHours() >= 1) {
                long hours = late.toHours();
                return hours + (hours == 1 ? " hour overdue" : " hours overdue");
            }
            return Math.max(1, late.toMinutes()) + " minutes overdue";
        }

        Duration until = Duration.between(generatedAt, start);
        if (until.toDays() >= 1) {
            return "tomorrow at " + appointment.getAppointmentTime();
        }
        if (until.toHours() >= 1) {
            long hours = until.toHours();
            return "in " + hours + (hours == 1 ? " hour" : " hours");
        }
        return "in " + Math.max(1, until.toMinutes()) + " minutes";
    }

    @Override
    public String toString() {
        return "Reminder{" + type + ", " + appointment.getAppointmentNo() + '}';
    }
}
