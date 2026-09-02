package lk.sunrisedental.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the decision-support reports.
 *
 * <p>Every method here calls one of the reporting stored procedures. Grouping, counting and
 * summing across five joined tables is set-based work, and doing it in the database means one
 * result set crosses the wire instead of every underlying row. What the numbers mean, and who is
 * allowed to see them, stays in {@code ReportService} - so the three-tier separation holds.</p>
 *
 * <p>Rows come back as {@link ReportRow} rather than typed DTOs because each procedure returns a
 * different shape and the service turns them straight into the Composite report tree.</p>
 */
public interface ReportDAO {

    /**
     * @param date the day to report on
     * @return one row per appointment, ordered by dentist then time
     */
    List<ReportRow> dailyAppointmentReport(LocalDate date);

    /**
     * @param from      inclusive start date
     * @param to        inclusive end date
     * @param dentistId a single dentist, or {@code null} for all
     * @return one row per dentist with workload and outcome counts
     */
    List<ReportRow> dentistAppointmentReport(LocalDate from, LocalDate to, Integer dentistId);

    /**
     * @param from inclusive start date
     * @param to   inclusive end date
     * @return one row per treatment with counts and revenue
     */
    List<ReportRow> treatmentRevenueReport(LocalDate from, LocalDate to);

    /**
     * @param from inclusive start date
     * @param to   inclusive end date
     * @return one row per day with bill counts and monetary totals
     */
    List<ReportRow> billingSummaryReport(LocalDate from, LocalDate to);

    /**
     * @param from inclusive start date
     * @param to   inclusive end date
     * @return one row per appointment status with its count
     */
    List<ReportRow> appointmentStatusSummary(LocalDate from, LocalDate to);

    /**
     * @param patientId the patient
     * @return every visit for that patient, most recent first
     */
    List<ReportRow> patientHistory(int patientId);

    /**
     * @param date the day the dashboard is showing
     * @return a single row of headline figures, or empty if the procedure returned nothing
     */
    Optional<ReportRow> dashboardSummary(LocalDate date);
}
