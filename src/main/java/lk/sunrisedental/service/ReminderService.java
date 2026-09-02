package lk.sunrisedental.service;

import lk.sunrisedental.dao.AppointmentDAO;
import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AppointmentStatus;
import lk.sunrisedental.model.Reminder;
import lk.sunrisedental.model.ReminderType;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the clinic's internal reminder queue.
 *
 * <p>Derived on demand from appointment data. There is no scheduler, no background thread, no
 * message broker and no external service - it is a query over records the system already holds,
 * shown on the dashboard. That is deliberate: a reminder feature that depends on SMTP credentials
 * cannot be tested in CI and stops working the moment mail configuration drifts.</p>
 *
 * <p>Two buckets, each answering a problem the clinic actually stated:</p>
 *
 * <ul>
 *   <li><strong>Upcoming</strong> - still scheduled and due within the look-ahead window. Staff can
 *       confirm tomorrow's patients in advance, which is how no-shows and the resulting idle
 *       chair time are reduced.</li>
 *   <li><strong>Awaiting outcome</strong> - the slot has passed but nobody has recorded whether the
 *       patient attended. These are the appointments that would otherwise sit in limbo forever,
 *       never billed and never counted in a report.</li>
 * </ul>
 *
 * <p>The clock is injected, so "due tomorrow" and "already overdue" can be tested against fixed
 * data rather than by waiting.</p>
 */
public class ReminderService {

    /** How far ahead an appointment must be to appear in the upcoming list. */
    private static final int DEFAULT_LOOK_AHEAD_DAYS = 1;

    private final AppointmentDAO appointmentDAO;
    private final Clock clock;

    public ReminderService(AppointmentDAO appointmentDAO, Clock clock) {
        this.appointmentDAO = appointmentDAO;
        this.clock = clock;
    }

    /**
     * @return every reminder due now, most urgent first
     */
    public List<Reminder> buildQueue() {
        return buildQueue(DEFAULT_LOOK_AHEAD_DAYS);
    }

    /**
     * @param lookAheadDays how many days ahead to include
     * @return every reminder due now, overdue outcomes first, then upcoming by time
     */
    public List<Reminder> buildQueue(int lookAheadDays) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        List<Reminder> reminders = new ArrayList<>();

        // Appointments whose slot has passed with no outcome recorded.
        for (Appointment appointment : appointmentDAO.findOverdueScheduled(now)) {
            reminders.add(new Reminder(appointment, ReminderType.AWAITING_OUTCOME, now));
        }

        // Appointments coming up inside the window, still scheduled.
        LocalDate horizon = today.plusDays(lookAheadDays);
        for (Appointment appointment : appointmentDAO.findByDateRange(today, horizon)) {
            if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
                continue;
            }
            LocalDateTime start = appointment.getStartDateTime();
            if (start != null && !start.isBefore(now)) {
                reminders.add(new Reminder(appointment, ReminderType.UPCOMING, now));
            }
        }

        // Overdue items first - they are the ones blocking billing and reporting - then the
        // soonest upcoming appointment.
        reminders.sort(Comparator
                .comparing((Reminder reminder) -> reminder.getType().getPriority())
                .thenComparing(reminder -> reminder.getAppointment().getStartDateTime(),
                        Comparator.nullsLast(Comparator.naturalOrder())));

        return reminders;
    }

    /**
     * @return how many appointments have passed their slot without an outcome being recorded
     */
    public int countAwaitingOutcome() {
        return appointmentDAO.findOverdueScheduled(LocalDateTime.now(clock)).size();
    }
}
