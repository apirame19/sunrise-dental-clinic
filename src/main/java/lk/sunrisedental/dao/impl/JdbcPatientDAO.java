package lk.sunrisedental.dao.impl;

import lk.sunrisedental.dao.PatientDAO;
import lk.sunrisedental.dao.SqlErrorTranslator;
import lk.sunrisedental.model.Patient;
import lk.sunrisedental.patterns.singleton.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO - plain JDBC data access for patients.
 *
 * <p>Every method is written out in full so the database work is visible: borrow a
 * {@link Connection} from the {@link DatabaseConnectionManager} singleton, prepare a
 * {@link PreparedStatement}, then read a {@link ResultSet}. Everything sits in try-with-resources,
 * so each resource is closed on every path including exceptions.</p>
 *
 * <p>Values are always bound with {@code statement.setX(...)} and never concatenated into the SQL
 * text, which is what makes SQL injection impossible here rather than merely unlikely.</p>
 *
 * <p><strong>Two methods take a connection instead of borrowing one.</strong>
 * {@link #insert(Patient, Connection)} and
 * {@link #findByNameAndContact(Connection, String, String)} are used inside the transaction that
 * registers a patient and their first appointment together. They close only the statement and
 * result set - the connection belongs to the caller, and closing it half way through would commit
 * the patient and lose the booking.</p>
 */
public class JdbcPatientDAO implements PatientDAO {

    private static final String COLUMNS =
            "patient_id, patient_name, address, contact_number, email, created_at";

    private static final String SELECT_BY_ID =
            "SELECT " + COLUMNS + " FROM patients WHERE patient_id = ?";

    private static final String SELECT_BY_IDENTITY =
            "SELECT " + COLUMNS + " FROM patients WHERE patient_name = ? AND contact_number = ?";

    private static final String SEARCH =
            "SELECT " + COLUMNS + " FROM patients "
            + "WHERE patient_name LIKE ? OR contact_number LIKE ? "
            + "ORDER BY patient_name LIMIT 200";

    private static final String SELECT_ALL =
            "SELECT " + COLUMNS + " FROM patients ORDER BY patient_name";

    private static final String INSERT =
            "INSERT INTO patients (patient_name, address, contact_number, email) VALUES (?, ?, ?, ?)";

    private static final String UPDATE =
            "UPDATE patients SET address = ?, contact_number = ?, email = ? WHERE patient_id = ?";

    private static final String COUNT = "SELECT COUNT(*) FROM patients";

    private static final String SELECT_BY_USER =
            "SELECT " + COLUMNS + " FROM patients WHERE user_id = ?";

    private static final String LINK_TO_USER =
            "UPDATE patients SET user_id = ? WHERE patient_id = ?";

    /** SINGLETON - the application's one source of MySQL connections. */
    private final DatabaseConnectionManager connectionManager;

    public JdbcPatientDAO() {
        this(DatabaseConnectionManager.getInstance());
    }

    /**
     * @param connectionManager the source of connections; injectable so a test can point this DAO
     *                          at a test schema
     */
    public JdbcPatientDAO(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // ------------------------------------------------------------------ reads

    @Override
    public Optional<Patient> findById(int patientId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {

            statement.setInt(1, patientId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapPatient(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading patient " + patientId);
        }
    }

    @Override
    public Optional<Patient> findByNameAndContact(String patientName, String contactNumber) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_IDENTITY)) {

            statement.setString(1, patientName);
            statement.setString(2, contactNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapPatient(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Looking up an existing patient");
        }
    }

    /**
     * As {@link #findByNameAndContact(String, String)}, but on the caller's transactional
     * connection.
     *
     * <p>The connection is NOT closed here. It is owned by the caller - the transaction that
     * registers a patient and their first appointment - and must stay open for the rest of that
     * unit of work. Only the statement and result set are closed.</p>
     */
    @Override
    public Optional<Patient> findByNameAndContact(Connection connection, String patientName,
                                                  String contactNumber) {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_IDENTITY)) {

            statement.setString(1, patientName);
            statement.setString(2, contactNumber);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapPatient(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Looking up an existing patient");
        }
    }

    @Override
    public List<Patient> search(String searchTerm) {
        // The wildcards are added to the BOUND VALUE, never to the SQL text. The search term
        // stays a parameter, so a term containing quotes is data and not syntax.
        String pattern = "%" + escapeLikeWildcards(searchTerm) + "%";
        List<Patient> patients = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SEARCH)) {

            statement.setString(1, pattern);
            statement.setString(2, pattern);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    patients.add(mapPatient(resultSet));
                }
            }
            return patients;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Searching patients");
        }
    }

    @Override
    public List<Patient> findAll() {
        List<Patient> patients = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                patients.add(mapPatient(resultSet));
            }
            return patients;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Listing all patients");
        }
    }

    @Override
    public long count() {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT);
             ResultSet resultSet = statement.executeQuery()) {

            return resultSet.next() ? resultSet.getLong(1) : 0L;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Counting patients");
        }
    }

    // ------------------------------------------------------------------ writes

    /**
     * Inserts a patient on the caller's transactional connection.
     *
     * <p>The connection is NOT closed here - the caller owns it. Only the statement and the
     * generated-keys result set are closed.</p>
     *
     * @return the generated patient id
     */
    @Override
    public int insert(Patient patient, Connection connection) {
        try (PreparedStatement statement =
                     connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, patient.getPatientName());
            statement.setString(2, patient.getAddress());
            statement.setString(3, patient.getContactNumber());
            statement.setString(4, patient.getEmail());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                // The row went in but we cannot tell the caller which id it got, which would
                // leave them holding a patient they cannot reference.
                throw new SQLException("Registering a patient returned no generated key");
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Registering patient");
        }
    }

    /**
     * Inserts a patient on its own connection, borrowed and closed here.
     *
     * <p>Delegates to {@link #insert(Patient, Connection)} so the INSERT and its key handling are
     * written once. The try-with-resources below owns this connection, and the delegate correctly
     * leaves it open.</p>
     */
    @Override
    public int insert(Patient patient) {
        try (Connection connection = connectionManager.getConnection()) {
            return insert(patient, connection);
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Registering patient");
        }
    }

    @Override
    public boolean update(Patient patient) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {

            statement.setString(1, patient.getAddress());
            statement.setString(2, patient.getContactNumber());
            statement.setString(3, patient.getEmail());
            statement.setInt(4, patient.getPatientId());

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Updating patient " + patient.getPatientId());
        }
    }

    // ------------------------------------------------------------------ account link

    @Override
    public Optional<Patient> findByUserId(int userId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_USER)) {

            statement.setInt(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapPatient(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading the patient record for an account");
        }
    }

    /**
     * Binds a patient record to a login on the caller's transactional connection.
     *
     * <p>The connection is NOT closed here - the caller owns it. Only the statement is closed.</p>
     */
    @Override
    public boolean linkToUser(Connection connection, int patientId, int userId) {
        try (PreparedStatement statement = connection.prepareStatement(LINK_TO_USER)) {

            statement.setInt(1, userId);
            statement.setInt(2, patientId);

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Linking patient " + patientId + " to their account");
        }
    }

    // ------------------------------------------------------------------ row mapping

    /**
     * Copies the current row of {@code patients} into a {@link Patient}.
     *
     * <p>{@code created_at} is read with {@link ResultSet#getObject(String, Class)}, which returns
     * a {@code java.time} value directly and yields {@code null} for SQL NULL - avoiding both the
     * legacy {@code java.util.Date} conversions and the {@code wasNull()} dance.</p>
     */
    private static Patient mapPatient(ResultSet resultSet) throws SQLException {
        Patient patient = new Patient();
        patient.setPatientId(resultSet.getInt("patient_id"));
        patient.setPatientName(resultSet.getString("patient_name"));
        patient.setAddress(resultSet.getString("address"));
        patient.setContactNumber(resultSet.getString("contact_number"));
        patient.setEmail(resultSet.getString("email"));
        patient.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        return patient;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Escapes the characters that are wildcards inside a {@code LIKE} pattern.
     *
     * <p>Without this, a search for "50%" would match every patient, and one for "_" would match
     * everyone with a single-character name. This is not an injection defence - the value is still
     * bound as a parameter - it simply stops user text being read as pattern syntax.</p>
     */
    private static String escapeLikeWildcards(String term) {
        if (term == null) {
            return "";
        }
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
