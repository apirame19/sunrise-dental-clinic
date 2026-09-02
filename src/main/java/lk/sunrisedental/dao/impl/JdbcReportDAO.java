package lk.sunrisedental.dao.impl;

import lk.sunrisedental.dao.ReportDAO;
import lk.sunrisedental.dao.ReportRow;
import lk.sunrisedental.dao.SqlErrorTranslator;
import lk.sunrisedental.patterns.singleton.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO - plain JDBC access to the reporting stored procedures.
 *
 * <p>Every method follows the same visible steps: borrow a {@link Connection} from the
 * {@link DatabaseConnectionManager} singleton, prepare the {@code CALL}, bind its parameters, then
 * read the {@link ResultSet} - all inside try-with-resources.</p>
 *
 * <p>Procedures are invoked with {@code CALL proc(?)} through an ordinary
 * {@link PreparedStatement}. Each of these procedures returns exactly one result set and has no
 * OUT parameters, so a {@code CallableStatement} would add ceremony without adding capability -
 * and the parameters are still bound, never concatenated.</p>
 *
 * <p><strong>The calculations live in the database, not here.</strong> Each of the seven methods
 * below is a thin call onto a stored procedure that does the aggregating. That is deliberate: a
 * revenue total computed in SQL and a revenue total computed in Java are two definitions of the
 * same figure, and they drift.</p>
 *
 * <p>None of these methods takes a caller-supplied connection - a report is a single read, never
 * part of a wider transaction - so every method owns and closes its own.</p>
 */
public class JdbcReportDAO implements ReportDAO {

    private static final String DAILY_APPOINTMENT_REPORT =
            "CALL sp_daily_appointment_report(?)";

    private static final String DENTIST_APPOINTMENT_REPORT =
            "CALL sp_dentist_appointment_report(?, ?, ?)";

    private static final String TREATMENT_REVENUE_REPORT =
            "CALL sp_treatment_revenue_report(?, ?)";

    private static final String BILLING_SUMMARY_REPORT =
            "CALL sp_billing_summary_report(?, ?)";

    private static final String APPOINTMENT_STATUS_SUMMARY =
            "CALL sp_appointment_status_summary(?, ?)";

    private static final String PATIENT_HISTORY =
            "CALL sp_patient_history(?)";

    private static final String DASHBOARD_SUMMARY =
            "CALL sp_dashboard_summary(?)";

    /** SINGLETON - the application's one source of MySQL connections. */
    private final DatabaseConnectionManager connectionManager;

    public JdbcReportDAO() {
        this(DatabaseConnectionManager.getInstance());
    }

    /**
     * @param connectionManager the source of connections; injectable so a test can point this DAO
     *                          at a test schema
     */
    public JdbcReportDAO(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // ------------------------------------------------------------------ appointment reports

    @Override
    public List<ReportRow> dailyAppointmentReport(LocalDate date) {
        List<ReportRow> rows = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(DAILY_APPOINTMENT_REPORT)) {

            statement.setObject(1, date);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapRow(resultSet));
                }
            }
            return rows;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Running the daily appointment report");
        }
    }

    @Override
    public List<ReportRow> dentistAppointmentReport(LocalDate from, LocalDate to,
                                                    Integer dentistId) {
        List<ReportRow> rows = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(DENTIST_APPOINTMENT_REPORT)) {

            statement.setObject(1, from);
            statement.setObject(2, to);
            // A null dentist means "every dentist"; the procedure treats SQL NULL that way.
            if (dentistId == null) {
                statement.setNull(3, Types.INTEGER);
            } else {
                statement.setInt(3, dentistId);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapRow(resultSet));
                }
            }
            return rows;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Running the dentist appointment report");
        }
    }

    @Override
    public List<ReportRow> appointmentStatusSummary(LocalDate from, LocalDate to) {
        List<ReportRow> rows = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(APPOINTMENT_STATUS_SUMMARY)) {

            statement.setObject(1, from);
            statement.setObject(2, to);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapRow(resultSet));
                }
            }
            return rows;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Running the appointment status summary");
        }
    }

    // ------------------------------------------------------------------ financial reports

    @Override
    public List<ReportRow> treatmentRevenueReport(LocalDate from, LocalDate to) {
        List<ReportRow> rows = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(TREATMENT_REVENUE_REPORT)) {

            statement.setObject(1, from);
            statement.setObject(2, to);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapRow(resultSet));
                }
            }
            return rows;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Running the treatment revenue report");
        }
    }

    @Override
    public List<ReportRow> billingSummaryReport(LocalDate from, LocalDate to) {
        List<ReportRow> rows = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(BILLING_SUMMARY_REPORT)) {

            statement.setObject(1, from);
            statement.setObject(2, to);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapRow(resultSet));
                }
            }
            return rows;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Running the billing summary report");
        }
    }

    // ------------------------------------------------------------------ patient and dashboard

    @Override
    public List<ReportRow> patientHistory(int patientId) {
        List<ReportRow> rows = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(PATIENT_HISTORY)) {

            statement.setInt(1, patientId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapRow(resultSet));
                }
            }
            return rows;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading patient history");
        }
    }

    @Override
    public Optional<ReportRow> dashboardSummary(LocalDate date) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(DASHBOARD_SUMMARY)) {

            statement.setObject(1, date);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading the dashboard summary");
        }
    }

    // ------------------------------------------------------------------ row mapping

    /**
     * Maps whatever columns the procedure returned, without the DAO needing to know them.
     *
     * <p>Column labels are read from the {@link ResultSetMetaData}, so adding a column to a
     * reporting procedure does not require a matching change here. Values are fetched with
     * {@link ResultSet#getObject(int)}; Connector/J returns {@code BigDecimal} for {@code DECIMAL}
     * and {@code java.time} types for dates, which is exactly what {@link ReportRow} expects.</p>
     *
     * <p>A {@link LinkedHashMap} is used so the columns keep the order the procedure declared
     * them in - that order is the report's column order on screen.</p>
     */
    private static ReportRow mapRow(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 1; i <= columnCount; i++) {
            values.put(metaData.getColumnLabel(i), resultSet.getObject(i));
        }
        return new ReportRow(values);
    }
}
