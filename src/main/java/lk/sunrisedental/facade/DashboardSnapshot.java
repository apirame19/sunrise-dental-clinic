package lk.sunrisedental.facade;

import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.Reminder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the dashboard shows, assembled in one call.
 *
 * <p>Gathered by the facade so the view issues no queries of its own. A JSP that fetched its own
 * figures would be business logic in the presentation tier, and it would also be slow: the counts
 * below come from a single stored-procedure round trip rather than nine separate ones.</p>
 *
 * <p>{@code todayRevenue} is {@code null} for a role that may not see financial figures, rather
 * than zero. Zero is a number the viewer would reasonably believe; null makes the view omit the
 * tile entirely, so nobody is misled about a quiet day.</p>
 *
 * @param date               the day being shown
 * @param todayTotal         appointments booked that day, in any status
 * @param todayScheduled     still expected
 * @param todayCompleted     seen and treated
 * @param todayCancelled     called off
 * @param todayNoShow        did not attend
 * @param todayRevenue       billed that day, or {@code null} if the viewer may not see it
 * @param totalPatients      patients on file
 * @param activeDentists     dentists currently practising
 * @param upcomingScheduled  future appointments still scheduled
 * @param todayAppointments  the day's list, ordered by dentist then time
 * @param reminders          the reminder queue, most urgent first
 */
public record DashboardSnapshot(LocalDate date,
                                long todayTotal,
                                long todayScheduled,
                                long todayCompleted,
                                long todayCancelled,
                                long todayNoShow,
                                BigDecimal todayRevenue,
                                long totalPatients,
                                long activeDentists,
                                long upcomingScheduled,
                                List<Appointment> todayAppointments,
                                List<Reminder> reminders) {

    /** @return {@code true} if the viewer is permitted to see the takings tile. */
    public boolean revenueVisible() {
        return todayRevenue != null;
    }

    /** @return how many appointments have passed their slot with no outcome recorded. */
    public long awaitingOutcomeCount() {
        return reminders.stream().filter(Reminder::isOverdue).count();
    }

    /** @return {@code true} if nothing is booked for the day, so the view can say so plainly. */
    public boolean isQuietDay() {
        return todayTotal == 0;
    }
}
