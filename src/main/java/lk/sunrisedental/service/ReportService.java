package lk.sunrisedental.service;

import lk.sunrisedental.dao.ReportDAO;
import lk.sunrisedental.dao.ReportRow;
import lk.sunrisedental.model.AppointmentStatus;
import lk.sunrisedental.patterns.composite.ReportLine;
import lk.sunrisedental.patterns.composite.ReportSection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the reporting procedures' flat result sets into Composite report trees.
 *
 * <p>This is where the Composite pattern earns its place. Each method below builds a tree of
 * {@link ReportSection} branches and {@link ReportLine} leaves; every subtotal is then produced by
 * recursion over the rows actually present, never accumulated in a variable alongside them. A
 * per-dentist subtotal on the daily report cannot disagree with the appointments printed under it,
 * because it <em>is</em> those appointments summed.</p>
 *
 * <p>The trees vary in depth - the daily report is three levels, the billing summary two, the
 * clinic overview four - and the JSP renders all of them with one recursive fragment, because a
 * section and a line answer the same questions.</p>
 *
 * <p>No authorisation happens here. Who may see revenue figures is decided once, by
 * {@code ClinicManagementFacade}, so the rule cannot be applied differently by the HTML and JSON
 * channels.</p>
 */
public class ReportService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy");
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final ReportDAO reportDAO;

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    /**
     * Every appointment on one day, grouped by dentist.
     *
     * <p>Three levels: report → dentist → appointment. The dentist subtotal is the revenue billed
     * against that dentist's completed visits, and the root total is the day's takings.</p>
     *
     * @param date the day to report on
     * @return the report tree; a section with no children when the clinic had no bookings
     */
    public ReportSection dailyAppointmentReport(LocalDate date) {
        ReportSection root = new ReportSection(
                "Daily appointment report", date.format(DAY));

        Map<Integer, ReportSection> byDentist = new LinkedHashMap<>();

        for (ReportRow row : reportDAO.dailyAppointmentReport(date)) {
            ReportSection dentistSection = byDentist.computeIfAbsent(
                    row.getInt("dentist_id"),
                    id -> new ReportSection(
                            row.getString("dentist_name", "Unknown dentist"),
                            row.getString("specialization", "")));

            dentistSection.add(new ReportLine(
                    row.getString("patient_name", "Unknown patient"),
                    time(row, "appointment_time") + " - "
                            + row.getString("treatment_name", "Treatment"),
                    row.getMoney("bill_total"))
                    .withColumn("Appointment", row.getString("appointment_no", ""))
                    .withColumn("Time", time(row, "appointment_time"))
                    .withColumn("Ends", time(row, "appointment_end_time"))
                    .withColumn("Treatment", row.getString("treatment_name", ""))
                    .withColumn("Contact", row.getString("contact_number", ""))
                    .withColumn("Status", statusLabel(row.getString("status")))
                    .withColumn("Bill", row.getString("bill_no", "Not billed")));
        }

        byDentist.values().forEach(root::add);
        return root;
    }

    /**
     * Workload and outcome mix per dentist over a period.
     *
     * <p>This is the report that answers whether one dentist is over-booked while another has
     * capacity - the clinic's "long waiting times" problem, stated as data.</p>
     *
     * @param from      inclusive start
     * @param to        inclusive end
     * @param dentistId a single dentist, or {@code null} for all
     * @return the report tree
     */
    public ReportSection dentistAppointmentReport(LocalDate from, LocalDate to, Integer dentistId) {
        ReportSection root = new ReportSection("Dentist appointment report", period(from, to));

        for (ReportRow row : reportDAO.dentistAppointmentReport(from, to, dentistId)) {
            long booked = row.getLong("booked_minutes");

            root.add(new ReportLine(
                    row.getString("dentist_name", "Unknown dentist"),
                    row.getString("specialization", ""),
                    row.getMoney("revenue"))
                    .withColumn("Appointments", Long.toString(row.getLong("total_appointments")))
                    .withColumn("Scheduled", Long.toString(row.getLong("scheduled_count")))
                    .withColumn("Completed", Long.toString(row.getLong("completed_count")))
                    .withColumn("Cancelled", Long.toString(row.getLong("cancelled_count")))
                    .withColumn("No show", Long.toString(row.getLong("no_show_count")))
                    .withColumn("Chair time", hoursAndMinutes(booked)));
        }
        return root;
    }

    /**
     * Revenue by treatment type, split into what earned and what did not.
     *
     * <p>The empty group is deliberately kept rather than filtered away. A treatment that was never
     * billed in a period is a finding - it is either not being offered or not being charged for -
     * and a report that silently omits its own zero rows hides exactly that.</p>
     *
     * @param from inclusive start
     * @param to   inclusive end
     * @return the report tree
     */
    public ReportSection treatmentRevenueReport(LocalDate from, LocalDate to) {
        ReportSection root = new ReportSection("Treatment and revenue report", period(from, to));

        ReportSection earning = new ReportSection("Treatments billed in this period", null);
        ReportSection idle = new ReportSection("Treatments with no billed work", null);

        for (ReportRow row : reportDAO.treatmentRevenueReport(from, to)) {
            long timesBilled = row.getLong("times_billed");

            ReportLine line = new ReportLine(
                    row.getString("treatment_name", "Treatment"),
                    row.getString("treatment_code", ""),
                    row.getMoney("gross_revenue"))
                    .withColumn("Code", row.getString("treatment_code", ""))
                    .withColumn("List price", plain(row.getMoney("base_cost")))
                    .withColumn("Times billed", Long.toString(timesBilled))
                    .withColumn("Treatment income", plain(row.getMoney("treatment_revenue")))
                    .withColumn("Consultation", plain(row.getMoney("consultation_revenue")))
                    .withColumn("Discounts", plain(row.getMoney("total_discount")))
                    .withColumn("Levy", plain(row.getMoney("total_tax")));

            if (timesBilled > 0) {
                earning.add(line);
            } else {
                idle.add(line);
            }
        }

        root.add(earning);
        root.add(idle);
        return root;
    }

    /**
     * Bills issued per day, with the discount and levy split visible.
     *
     * @param from inclusive start
     * @param to   inclusive end
     * @return the report tree
     */
    public ReportSection billingSummaryReport(LocalDate from, LocalDate to) {
        ReportSection root = new ReportSection("Billing summary", period(from, to));

        for (ReportRow row : reportDAO.billingSummaryReport(from, to)) {
            LocalDate billDate = row.getDate("bill_date");

            root.add(new ReportLine(
                    billDate == null ? "Unknown date" : billDate.format(SHORT_DAY),
                    row.getLong("bill_count") + " bill"
                            + (row.getLong("bill_count") == 1 ? "" : "s"),
                    row.getMoney("grand_total"))
                    .withColumn("Bills", Long.toString(row.getLong("bill_count")))
                    .withColumn("Treatment", plain(row.getMoney("treatment_total")))
                    .withColumn("Consultation", plain(row.getMoney("consultation_total")))
                    .withColumn("Discounts", plain(row.getMoney("discount_total")))
                    .withColumn("Levy", plain(row.getMoney("tax_total")))
                    .withColumn("Average bill", plain(row.getMoney("average_bill"))));
        }
        return root;
    }

    /**
     * Counts by appointment status for a period.
     *
     * <p>Every status appears, including those with no appointments. A no-show count that is
     * missing rather than zero is indistinguishable from a report that failed to run.</p>
     *
     * @param from inclusive start
     * @param to   inclusive end
     * @return the report tree
     */
    public ReportSection appointmentStatusSummary(LocalDate from, LocalDate to) {
        ReportSection root = new ReportSection("Appointment status summary", period(from, to));

        Map<AppointmentStatus, ReportRow> found = new LinkedHashMap<>();
        for (ReportRow row : reportDAO.appointmentStatusSummary(from, to)) {
            AppointmentStatus.fromString(row.getString("status"))
                    .ifPresent(status -> found.put(status, row));
        }

        long total = found.values().stream().mapToLong(row -> row.getLong("status_count")).sum();

        for (AppointmentStatus status : AppointmentStatus.values()) {
            ReportRow row = found.get(status);
            long count = row == null ? 0L : row.getLong("status_count");
            BigDecimal billed = row == null ? BigDecimal.ZERO : row.getMoney("billed_amount");

            root.add(new ReportLine(status.getLabel(), null, billed)
                    .withColumn("Appointments", Long.toString(count))
                    .withColumn("Share", percentage(count, total))
                    .withColumn("Billed", plain(billed)));
        }
        return root;
    }

    /**
     * One patient's complete visit history, grouped by year.
     *
     * <p>Grouping by year is what makes a long-standing patient's record readable: the year
     * subtotals show what they have spent with the clinic and when, without anyone adding rows up
     * by hand.</p>
     *
     * @param patientId  the patient
     * @param patientName the patient's name, for the report heading
     * @return the report tree
     */
    public ReportSection patientHistoryReport(int patientId, String patientName) {
        ReportSection root = new ReportSection(
                "Patient history",
                patientName == null ? "Patient " + patientId : patientName);

        Map<Integer, ReportSection> byYear = new LinkedHashMap<>();

        for (ReportRow row : reportDAO.patientHistory(patientId)) {
            LocalDate visitDate = row.getDate("appointment_date");
            int year = visitDate == null ? 0 : visitDate.getYear();

            ReportSection yearSection = byYear.computeIfAbsent(year,
                    key -> new ReportSection(key == 0 ? "Undated" : Integer.toString(key), null));

            yearSection.add(new ReportLine(
                    row.getString("treatment_name", "Treatment"),
                    (visitDate == null ? "" : visitDate.format(SHORT_DAY) + " ")
                            + time(row, "appointment_time"),
                    row.getMoney("bill_total"))
                    .withColumn("Appointment", row.getString("appointment_no", ""))
                    .withColumn("Date", visitDate == null ? "" : visitDate.format(SHORT_DAY))
                    .withColumn("Dentist", row.getString("dentist_name", ""))
                    .withColumn("Treatment", row.getString("treatment_name", ""))
                    .withColumn("Status", statusLabel(row.getString("status")))
                    .withColumn("Bill", row.getString("bill_no", "Not billed")));
        }

        byYear.values().forEach(root::add);
        return root;
    }

    /**
     * The management overview: three reports nested inside one.
     *
     * <p>A composite of composites, and the clearest demonstration of why the pattern was chosen.
     * The overview does not know that its children are reports rather than rows - it adds them,
     * and its grand total falls out of the recursion. Adding a fourth section later needs no change
     * to the totalling or to the view.</p>
     *
     * @param date the day the overview covers
     * @return a four-level report tree
     */
    public ReportSection clinicOverview(LocalDate date) {
        ReportSection root = new ReportSection("Clinic overview", date.format(DAY));
        root.add(dailyAppointmentReport(date));
        root.add(appointmentStatusSummary(date, date));
        root.add(billingSummaryReport(date, date));
        return root;
    }

    /**
     * @param date the day the dashboard is showing
     * @return the headline figures, or empty if the procedure returned nothing
     */
    public Optional<ReportRow> dashboardSummary(LocalDate date) {
        return reportDAO.dashboardSummary(date);
    }

    /** @return the reports offered, for the report menu; the key is the servlet's {@code type}. */
    public static List<String> availableReportTypes() {
        return List.of("daily", "dentist", "treatment", "billing", "status", "overview");
    }

    // ------------------------------------------------------------------ formatting

    private static String period(LocalDate from, LocalDate to) {
        return from.equals(to)
                ? from.format(DAY)
                : from.format(SHORT_DAY) + " to " + to.format(SHORT_DAY);
    }

    private static String time(ReportRow row, String column) {
        return row.getTime(column) == null ? "" : row.getTime(column).format(CLOCK);
    }

    private static String statusLabel(String raw) {
        return AppointmentStatus.fromString(raw)
                .map(AppointmentStatus::getLabel)
                .orElse(raw == null ? "" : raw);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0.00" : value.toPlainString();
    }

    private static String percentage(long part, long whole) {
        if (whole <= 0) {
            return "0%";
        }
        return Math.round(part * 100.0 / whole) + "%";
    }

    /** @return for example {@code 4h 30m}, which reads faster than a minute count. */
    private static String hoursAndMinutes(long minutes) {
        if (minutes <= 0) {
            return "0m";
        }
        long hours = minutes / 60;
        long remainder = minutes % 60;
        if (hours == 0) {
            return remainder + "m";
        }
        return remainder == 0 ? hours + "h" : hours + "h " + remainder + "m";
    }
}
