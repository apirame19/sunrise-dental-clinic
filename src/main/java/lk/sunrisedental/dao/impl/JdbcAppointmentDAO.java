package lk.sunrisedental.dao.impl;

import lk.sunrisedental.dao.AppointmentDAO;
import lk.sunrisedental.dao.SqlErrorTranslator;
import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AppointmentStatus;
import lk.sunrisedental.model.Dentist;
import lk.sunrisedental.model.Patient;
import lk.sunrisedental.model.Treatment;
import lk.sunrisedental.patterns.singleton.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO - plain JDBC data access for appointments, the busiest table in the system.
 *
 * <p>Every method is written out in full so the database work is visible: borrow a
 * {@link Connection} from the {@link DatabaseConnectionManager} singleton, prepare a
 * {@link PreparedStatement}, then read a {@link ResultSet}, all inside try-with-resources.</p>
 *
 * <p>Reads go through {@code vw_appointment_details}, which is the canonical five-table join.
 * Defining that join once in the database rather than in each of the eight find methods below is
 * what stops the search screen and the daily report quietly disagreeing about what an appointment
 * is. Because the view already joins patient, dentist, treatment and user, {@link #mapAppointment}
 * builds the whole object graph from one row instead of firing four follow-up queries.</p>
 *
 * <p>Three operations are delegated to stored functions -
 * {@code fn_is_dentist_available}, {@code fn_conflicting_appointment_no} and
 * {@code fn_next_appointment_no}. They are called with an ordinary {@code PreparedStatement} and
 * bound parameters; the overlap comparison lives in the database because that is the only place it
 * can be evaluated atomically against current data.</p>
 *
 * <p><strong>Two methods take a connection instead of borrowing one.</strong>
 * {@link #existsByAppointmentNo(Connection, String)} and
 * {@link #insert(Appointment, int, Connection)} run inside the transaction that registers a patient
 * and their first appointment together. They close only the statement and result set - the
 * connection belongs to the caller.</p>
 */
public class JdbcAppointmentDAO implements AppointmentDAO {

    private static final String VIEW_COLUMNS =
            "appointment_id, appointment_no, appointment_date, appointment_time, status, notes, "
            + "cancel_reason, created_at, updated_at, patient_id, patient_name, address, "
            + "contact_number, email, dentist_id, dentist_name, specialization, treatment_id, "
            + "treatment_code, treatment_name, base_cost, duration_minutes, is_taxable, "
            + "created_by_id, created_by_name, bill_id, bill_no";

    private static final String SELECT_BY_NO =
            "SELECT " + VIEW_COLUMNS + " FROM vw_appointment_details WHERE appointment_no = ?";

    private static final String SELECT_BY_ID =
            "SELECT " + VIEW_COLUMNS + " FROM vw_appointment_details WHERE appointment_id = ?";

    private static final String EXISTS_BY_NO =
            "SELECT COUNT(*) FROM appointments WHERE appointment_no = ?";

    private static final String SELECT_BY_DATE =
            "SELECT " + VIEW_COLUMNS + " FROM vw_appointment_details "
            + "WHERE appointment_date = ? ORDER BY dentist_name, appointment_time";

    private static final String SELECT_BY_DATE_RANGE =
            "SELECT " + VIEW_COLUMNS + " FROM vw_appointment_details "
            + "WHERE appointment_date BETWEEN ? AND ? "
            + "ORDER BY appointment_date, appointment_time";

    private static final String SELECT_BY_PATIENT =
            "SELECT " + VIEW_COLUMNS + " FROM vw_appointment_details "
            + "WHERE patient_id = ? ORDER BY appointment_date DESC, appointment_time DESC";

    private static final String SELECT_BY_DENTIST_DATE =
            "SELECT " + VIEW_COLUMNS + " FROM vw_appointment_details "
            + "WHERE dentist_id = ? AND appointment_date = ? ORDER BY appointment_time";

    private static final String SELECT_BY_DATE_STATUS =
            "SELECT " + VIEW_COLUMNS + " FROM vw_appointment_details "
            + "WHERE appointment_date = ? AND status = ? ORDER BY appointment_time";

    private static final String SELECT_OVERDUE =
            "SELECT " + VIEW_COLUMNS + " FROM vw_appointment_details "
            + "WHERE status = 'SCHEDULED' "
            + "AND TIMESTAMP(appointment_date, appointment_end_time) < ? "
            + "ORDER BY appointment_date, appointment_time";

    private static final String INSERT =
            "INSERT INTO appointments (appointment_no, patient_id, dentist_id, treatment_id, "
            + "appointment_date, appointment_time, status, notes, created_by) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_STATUS =
            "UPDATE appointments SET status = ?, cancel_reason = ? WHERE appointment_id = ?";

    private static final String IS_AVAILABLE =
            "SELECT fn_is_dentist_available(?, ?, ?, ?, ?)";

    private static final String CONFLICTING_NO =
            "SELECT fn_conflicting_appointment_no(?, ?, ?, ?, ?)";

    private static final String NEXT_APPOINTMENT_NO =
            "SELECT fn_next_appointment_no(?)";

    private static final String COUNT_BY_STATUS =
            "SELECT status, COUNT(*) AS status_count FROM appointments "
            + "WHERE appointment_date BETWEEN ? AND ? GROUP BY status";

    private static final String COUNT_BY_DATE =
            "SELECT COUNT(*) FROM appointments WHERE appointment_date = ?";

    /** SINGLETON - the application's one source of MySQL connections. */
    private final DatabaseConnectionManager connectionManager;

    public JdbcAppointmentDAO() {
        this(DatabaseConnectionManager.getInstance());
    }

    /**
     * @param connectionManager the source of connections; injectable so a test can point this DAO
     *                          at a test schema
     */
    public JdbcAppointmentDAO(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // ------------------------------------------------------------------ single-row reads

    @Override
    public Optional<Appointment> findByAppointmentNo(String appointmentNo) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_NO)) {

            statement.setString(1, appointmentNo);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapAppointment(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Searching for appointment " + appointmentNo);
        }
    }

    @Override
    public Optional<Appointment> findById(int appointmentId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {

            statement.setInt(1, appointmentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapAppointment(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading appointment " + appointmentId);
        }
    }

    @Override
    public boolean existsByAppointmentNo(String appointmentNo) {
        try (Connection connection = connectionManager.getConnection()) {
            return existsByAppointmentNo(connection, appointmentNo);
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Checking appointment number availability");
        }
    }

    /**
     * As above, but on the caller's transactional connection.
     *
     * <p>The connection is NOT closed here - it belongs to the caller and must stay open for the
     * rest of the transaction. Only the statement and result set are closed.</p>
     */
    @Override
    public boolean existsByAppointmentNo(Connection connection, String appointmentNo) {
        try (PreparedStatement statement = connection.prepareStatement(EXISTS_BY_NO)) {

            statement.setString(1, appointmentNo);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Checking appointment number availability");
        }
    }

    // ------------------------------------------------------------------ list reads

    @Override
    public List<Appointment> findByDate(LocalDate date) {
        List<Appointment> appointments = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_DATE)) {

            statement.setObject(1, date);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appointments.add(mapAppointment(resultSet));
                }
            }
            return appointments;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Listing appointments for " + date);
        }
    }

    @Override
    public List<Appointment> findByDateRange(LocalDate from, LocalDate to) {
        List<Appointment> appointments = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_DATE_RANGE)) {

            statement.setObject(1, from);
            statement.setObject(2, to);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appointments.add(mapAppointment(resultSet));
                }
            }
            return appointments;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Listing appointments between " + from + " and " + to);
        }
    }

    @Override
    public List<Appointment> findByPatientId(int patientId) {
        List<Appointment> appointments = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_PATIENT)) {

            statement.setInt(1, patientId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appointments.add(mapAppointment(resultSet));
                }
            }
            return appointments;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading history for patient " + patientId);
        }
    }

    @Override
    public List<Appointment> findByDentistAndDate(int dentistId, LocalDate date) {
        List<Appointment> appointments = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_DENTIST_DATE)) {

            statement.setInt(1, dentistId);
            statement.setObject(2, date);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appointments.add(mapAppointment(resultSet));
                }
            }
            return appointments;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading dentist schedule");
        }
    }

    @Override
    public List<Appointment> findByDateAndStatus(LocalDate date, AppointmentStatus status) {
        List<Appointment> appointments = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_DATE_STATUS)) {

            statement.setObject(1, date);
            statement.setString(2, status.name());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appointments.add(mapAppointment(resultSet));
                }
            }
            return appointments;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Listing appointments by status");
        }
    }

    @Override
    public List<Appointment> findOverdueScheduled(LocalDateTime asOf) {
        List<Appointment> appointments = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_OVERDUE)) {

            statement.setTimestamp(1, Timestamp.valueOf(asOf));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appointments.add(mapAppointment(resultSet));
                }
            }
            return appointments;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Listing appointments awaiting an outcome");
        }
    }

    // ------------------------------------------------------------------ writes

    /**
     * Inserts an appointment on the caller's transactional connection.
     *
     * <p>The connection is NOT closed here - the caller owns it. Only the statement and the
     * generated-keys result set are closed.</p>
     *
     * @return the generated appointment id
     */
    @Override
    public int insert(Appointment appointment, int createdBy, Connection connection) {
        try (PreparedStatement statement =
                     connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, appointment.getAppointmentNo());
            statement.setInt(2, appointment.getPatient().getPatientId());
            statement.setInt(3, appointment.getDentist().getDentistId());
            statement.setInt(4, appointment.getTreatment().getTreatmentId());
            statement.setObject(5, appointment.getAppointmentDate());
            statement.setObject(6, appointment.getAppointmentTime());
            statement.setString(7, appointment.getStatus().name());
            statement.setString(8, appointment.getNotes());
            statement.setInt(9, createdBy);

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                // The row went in but we cannot tell the caller which id it got, which would
                // leave them holding an appointment they cannot reference.
                throw new SQLException("Registering an appointment returned no generated key");
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Registering appointment " + appointment.getAppointmentNo());
        }
    }

    @Override
    public boolean updateStatus(int appointmentId, AppointmentStatus status, String cancelReason) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_STATUS)) {

            statement.setString(1, status.name());
            if (cancelReason == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, cancelReason);
            }
            statement.setInt(3, appointmentId);

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Updating status of appointment " + appointmentId);
        }
    }

    // ------------------------------------------------------------------ stored functions

    @Override
    public boolean isDentistAvailable(int dentistId, LocalDate date, LocalTime time,
                                      int durationMinutes, Integer excludeAppointmentId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(IS_AVAILABLE)) {

            bindAvailability(statement, dentistId, date, time, durationMinutes,
                    excludeAppointmentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Checking dentist availability");
        }
    }

    @Override
    public Optional<String> findConflictingAppointmentNo(int dentistId, LocalDate date,
                                                         LocalTime time, int durationMinutes,
                                                         Integer excludeAppointmentId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(CONFLICTING_NO)) {

            bindAvailability(statement, dentistId, date, time, durationMinutes,
                    excludeAppointmentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    // The function returns SQL NULL when the slot is free.
                    String conflictingNo = resultSet.getString(1);
                    if (conflictingNo != null && !conflictingNo.isBlank()) {
                        return Optional.of(conflictingNo);
                    }
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Finding the conflicting appointment");
        }
    }

    /**
     * Binds the five arguments shared by {@code fn_is_dentist_available} and
     * {@code fn_conflicting_appointment_no}.
     *
     * <p>{@code excludeAppointmentId} is the appointment to ignore when rescheduling one, and is
     * bound as SQL NULL when there is nothing to exclude.</p>
     */
    private static void bindAvailability(PreparedStatement statement, int dentistId, LocalDate date,
                                         LocalTime time, int durationMinutes,
                                         Integer excludeAppointmentId) throws SQLException {
        statement.setInt(1, dentistId);
        statement.setObject(2, date);
        statement.setObject(3, time);
        statement.setInt(4, durationMinutes);
        if (excludeAppointmentId == null) {
            statement.setNull(5, Types.INTEGER);
        } else {
            statement.setInt(5, excludeAppointmentId);
        }
    }

    @Override
    public String generateNextAppointmentNo(LocalDate date) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(NEXT_APPOINTMENT_NO)) {

            statement.setObject(1, date);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String appointmentNo = resultSet.getString(1);
                    if (appointmentNo != null) {
                        return appointmentNo;
                    }
                }
                throw new IllegalStateException("fn_next_appointment_no returned no value");
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Generating the next appointment number");
        }
    }

    // ------------------------------------------------------------------ counts

    @Override
    public Map<AppointmentStatus, Long> countByStatus(LocalDate from, LocalDate to) {
        // Every status is present in the result, with zero where the database returned no row.
        // A report that silently omits "no-shows: 0" reads as though no-shows were not measured.
        Map<AppointmentStatus, Long> counts = new EnumMap<>(AppointmentStatus.class);
        for (AppointmentStatus status : AppointmentStatus.values()) {
            counts.put(status, 0L);
        }

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_BY_STATUS)) {

            statement.setObject(1, from);
            statement.setObject(2, to);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String status = resultSet.getString("status");
                    long count = resultSet.getLong("status_count");
                    AppointmentStatus.fromString(status)
                            .ifPresent(known -> counts.put(known, count));
                }
            }
            return counts;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Counting appointments by status");
        }
    }

    @Override
    public long countByDate(LocalDate date) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_BY_DATE)) {

            statement.setObject(1, date);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Counting appointments on " + date);
        }
    }

    // ------------------------------------------------------------------ row mapping

    /**
     * Copies the current row of {@code vw_appointment_details} into a fully populated
     * {@link Appointment}.
     *
     * <p>The view is a five-table join, so the patient, dentist and treatment that belong to this
     * appointment are built here from columns of the same row. That is why this class refers to
     * those model types: they are the appointment's own object graph, not data access borrowed
     * from another DAO.</p>
     *
     * <p>Temporal columns are read with {@link ResultSet#getObject(String, Class)}, which returns
     * {@code java.time} values directly and yields {@code null} for SQL NULL.</p>
     */
    private static Appointment mapAppointment(ResultSet resultSet) throws SQLException {
        Appointment appointment = new Appointment();
        appointment.setAppointmentId(resultSet.getInt("appointment_id"));
        appointment.setAppointmentNo(resultSet.getString("appointment_no"));
        appointment.setAppointmentDate(resultSet.getObject("appointment_date", LocalDate.class));
        appointment.setAppointmentTime(resultSet.getObject("appointment_time", LocalTime.class));
        appointment.setStatus(AppointmentStatus.fromString(resultSet.getString("status"))
                .orElse(AppointmentStatus.SCHEDULED));
        appointment.setNotes(resultSet.getString("notes"));
        appointment.setCancelReason(resultSet.getString("cancel_reason"));
        appointment.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        appointment.setUpdatedAt(resultSet.getObject("updated_at", LocalDateTime.class));
        appointment.setCreatedById(resultSet.getInt("created_by_id"));
        appointment.setCreatedByName(resultSet.getString("created_by_name"));

        Patient patient = new Patient();
        patient.setPatientId(resultSet.getInt("patient_id"));
        patient.setPatientName(resultSet.getString("patient_name"));
        patient.setAddress(resultSet.getString("address"));
        patient.setContactNumber(resultSet.getString("contact_number"));
        patient.setEmail(resultSet.getString("email"));
        appointment.setPatient(patient);

        Dentist dentist = new Dentist();
        dentist.setDentistId(resultSet.getInt("dentist_id"));
        dentist.setDentistName(resultSet.getString("dentist_name"));
        dentist.setSpecialization(resultSet.getString("specialization"));
        appointment.setDentist(dentist);

        Treatment treatment = new Treatment();
        treatment.setTreatmentId(resultSet.getInt("treatment_id"));
        treatment.setTreatmentCode(resultSet.getString("treatment_code"));
        treatment.setTreatmentName(resultSet.getString("treatment_name"));
        treatment.setBaseCost(resultSet.getBigDecimal("base_cost"));
        treatment.setDurationMinutes(resultSet.getInt("duration_minutes"));
        treatment.setTaxable(resultSet.getBoolean("is_taxable"));
        appointment.setTreatment(treatment);

        // LEFT JOIN: null until the visit has been billed.
        int billId = resultSet.getInt("bill_id");
        if (!resultSet.wasNull() && billId > 0) {
            appointment.setBillId(billId);
            appointment.setBillNo(resultSet.getString("bill_no"));
        }
        return appointment;
    }
}
