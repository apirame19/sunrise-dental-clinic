package lk.sunrisedental.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * A patient's complete record: who they are, every visit, every bill, and the totals.
 *
 * <p>Assembled once by {@code PatientService} so the view renders a full history without issuing a
 * query per row. Immutable, because it is a snapshot of what was true when it was built.</p>
 *
 * <p>The counts are precomputed rather than left for the JSP to work out. A view that filters and
 * counts is a view containing business logic, and this is exactly the sort of small calculation
 * that ends up duplicated - and eventually inconsistent - across the history page, the dashboard
 * and the printed record.</p>
 */
public final class PatientHistory {

    private final Patient patient;
    private final List<Appointment> appointments;
    private final List<Bill> bills;
    private final BigDecimal totalBilled;
    private final long completedCount;
    private final long cancelledCount;
    private final long noShowCount;
    private final long upcomingCount;

    public PatientHistory(Patient patient,
                          List<Appointment> appointments,
                          List<Bill> bills,
                          BigDecimal totalBilled,
                          long completedCount,
                          long cancelledCount,
                          long noShowCount,
                          long upcomingCount) {
        this.patient = patient;
        this.appointments = List.copyOf(appointments);
        this.bills = List.copyOf(bills);
        this.totalBilled = totalBilled;
        this.completedCount = completedCount;
        this.cancelledCount = cancelledCount;
        this.noShowCount = noShowCount;
        this.upcomingCount = upcomingCount;
    }

    public Patient getPatient() {
        return patient;
    }

    /** @return every visit, most recent first. */
    public List<Appointment> getAppointments() {
        return Collections.unmodifiableList(appointments);
    }

    /** @return every bill issued to this patient. */
    public List<Bill> getBills() {
        return Collections.unmodifiableList(bills);
    }

    /** @return the sum of every bill issued to this patient. */
    public BigDecimal getTotalBilled() {
        return totalBilled;
    }

    public long getCompletedCount() {
        return completedCount;
    }

    public long getCancelledCount() {
        return cancelledCount;
    }

    public long getNoShowCount() {
        return noShowCount;
    }

    public long getUpcomingCount() {
        return upcomingCount;
    }

    /** @return the total number of visits on record. */
    public int getVisitCount() {
        return appointments.size();
    }

    /** @return {@code true} if this patient has never been seen, so the view can say so. */
    public boolean isEmpty() {
        return appointments.isEmpty();
    }

    /** @return the most recent visit, or null if there is none. */
    public Appointment getMostRecentVisit() {
        return appointments.isEmpty() ? null : appointments.get(0);
    }
}
