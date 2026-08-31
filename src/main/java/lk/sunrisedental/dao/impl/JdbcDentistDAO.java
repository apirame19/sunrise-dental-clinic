package lk.sunrisedental.dao.impl;

import lk.sunrisedental.dao.DentistDAO;
import lk.sunrisedental.dao.SqlErrorTranslator;
import lk.sunrisedental.model.Dentist;
import lk.sunrisedental.patterns.singleton.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO - plain JDBC data access for dentists.
 *
 * <p>Every method follows the same visible three steps: borrow a {@link Connection} from the
 * {@link DatabaseConnectionManager} singleton, prepare a {@link PreparedStatement}, then read a
 * {@link ResultSet} - all inside try-with-resources, so each resource is closed on every path
 * including exceptions.</p>
 *
 * <p>Values are always bound with {@code statement.setX(...)} and never concatenated into the SQL
 * text, which is what makes SQL injection impossible here rather than merely unlikely.</p>
 *
 * <p>Unlike the patient, appointment and bill DAOs, none of these methods takes a caller-supplied
 * connection: no clinic workflow writes a dentist as part of a larger transaction, so every method
 * owns and closes its own connection.</p>
 *
 * <p><strong>Dentists are withdrawn, never deleted.</strong> {@link #setActive} is the only removal
 * offered, because every past appointment references its dentist; deleting one would either be
 * refused by the foreign key or destroy the record of who treated whom. Note also that
 * {@link #update} does not touch {@code is_active} - changing a dentist's details and withdrawing
 * them are separate decisions, and folding them together would let an edit silently reinstate a
 * withdrawn dentist.</p>
 */
public class JdbcDentistDAO implements DentistDAO {

    private static final String COLUMNS =
            "dentist_id, dentist_name, specialization, license_no, contact_number, is_active";

    private static final String SELECT_BY_ID =
            "SELECT " + COLUMNS + " FROM dentists WHERE dentist_id = ?";

    private static final String SELECT_ACTIVE =
            "SELECT " + COLUMNS + " FROM dentists WHERE is_active = 1 ORDER BY dentist_name";

    private static final String SELECT_ALL =
            "SELECT " + COLUMNS + " FROM dentists ORDER BY is_active DESC, dentist_name";

    private static final String COUNT_ACTIVE =
            "SELECT COUNT(*) FROM dentists WHERE is_active = 1";

    private static final String EXISTS_ACTIVE =
            "SELECT COUNT(*) FROM dentists WHERE dentist_id = ? AND is_active = 1";

    private static final String SELECT_BY_LICENSE =
            "SELECT " + COLUMNS + " FROM dentists WHERE license_no = ?";

    private static final String INSERT =
            "INSERT INTO dentists (dentist_name, specialization, license_no, contact_number, "
            + "is_active) VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE =
            "UPDATE dentists SET dentist_name = ?, specialization = ?, license_no = ?, "
            + "contact_number = ? WHERE dentist_id = ?";

    private static final String SET_ACTIVE =
            "UPDATE dentists SET is_active = ? WHERE dentist_id = ?";

    private static final String SELECT_BY_USER =
            "SELECT " + COLUMNS + " FROM dentists WHERE user_id = ?";

    private static final String LINK_TO_USER =
            "UPDATE dentists SET user_id = ? WHERE dentist_id = ?";

    /** SINGLETON - the application's one source of MySQL connections. */
    private final DatabaseConnectionManager connectionManager;

    public JdbcDentistDAO() {
        this(DatabaseConnectionManager.getInstance());
    }

    /**
     * @param connectionManager the source of connections; injectable so a test can point this DAO
     *                          at a test schema
     */
    public JdbcDentistDAO(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // ------------------------------------------------------------------ single-row reads

    @Override
    public Optional<Dentist> findById(int dentistId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {

            statement.setInt(1, dentistId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapDentist(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading dentist " + dentistId);
        }
    }

    @Override
    public Optional<Dentist> findByLicenseNo(String licenseNo) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_LICENSE)) {

            statement.setString(1, licenseNo);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapDentist(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading dentist by licence number");
        }
    }

    // ------------------------------------------------------------------ list reads

    @Override
    public List<Dentist> findAllActive() {
        List<Dentist> dentists = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                dentists.add(mapDentist(resultSet));
            }
            return dentists;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Listing active dentists");
        }
    }

    @Override
    public List<Dentist> findAll() {
        List<Dentist> dentists = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                dentists.add(mapDentist(resultSet));
            }
            return dentists;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Listing all dentists");
        }
    }

    // ------------------------------------------------------------------ counts and checks

    @Override
    public boolean isActive(int dentistId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_ACTIVE)) {

            statement.setInt(1, dentistId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Checking dentist " + dentistId + " is active");
        }
    }

    @Override
    public long countActive() {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_ACTIVE);
             ResultSet resultSet = statement.executeQuery()) {

            return resultSet.next() ? resultSet.getLong(1) : 0L;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Counting active dentists");
        }
    }

    // ------------------------------------------------------------------ writes

    @Override
    public int insert(Dentist dentist) {
        try (Connection connection = connectionManager.getConnection()) {
            return insert(dentist, connection);
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Adding dentist");
        }
    }

    /**
     * Adds a dentist on the caller's transactional connection.
     *
     * <p>The connection is NOT closed here - the caller owns it. Only the statement and the
     * generated-keys result set are closed.</p>
     */
    @Override
    public int insert(Dentist dentist, Connection connection) {
        try (PreparedStatement statement =
                     connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, dentist.getDentistName());
            statement.setString(2, dentist.getSpecialization());
            statement.setString(3, dentist.getLicenseNo());
            statement.setString(4, dentist.getContactNumber());
            statement.setBoolean(5, dentist.isActive());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                // The row went in but we cannot tell the caller which id it got, which would
                // leave them holding a dentist they cannot reference.
                throw new SQLException("Adding a dentist returned no generated key");
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Adding dentist");
        }
    }

    // ------------------------------------------------------------------ account link

    @Override
    public Optional<Dentist> findByUserId(int userId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_USER)) {

            statement.setInt(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapDentist(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading the dentist record for an account");
        }
    }

    /**
     * Binds a dentist record to a login on the caller's transactional connection.
     *
     * <p>The connection is NOT closed here - the caller owns it. Only the statement is closed.</p>
     */
    @Override
    public boolean linkToUser(Connection connection, int dentistId, int userId) {
        try (PreparedStatement statement = connection.prepareStatement(LINK_TO_USER)) {

            statement.setInt(1, userId);
            statement.setInt(2, dentistId);

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Linking dentist " + dentistId + " to their account");
        }
    }

    @Override
    public boolean update(Dentist dentist) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {

            statement.setString(1, dentist.getDentistName());
            statement.setString(2, dentist.getSpecialization());
            statement.setString(3, dentist.getLicenseNo());
            statement.setString(4, dentist.getContactNumber());
            statement.setInt(5, dentist.getDentistId());

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Updating dentist " + dentist.getDentistId());
        }
    }

    @Override
    public boolean setActive(int dentistId, boolean active) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SET_ACTIVE)) {

            statement.setBoolean(1, active);
            statement.setInt(2, dentistId);

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    (active ? "Reinstating" : "Withdrawing") + " dentist " + dentistId);
        }
    }

    // ------------------------------------------------------------------ row mapping

    /** Copies the current row of {@code dentists} into a {@link Dentist}. */
    private static Dentist mapDentist(ResultSet resultSet) throws SQLException {
        Dentist dentist = new Dentist();
        dentist.setDentistId(resultSet.getInt("dentist_id"));
        dentist.setDentistName(resultSet.getString("dentist_name"));
        dentist.setSpecialization(resultSet.getString("specialization"));
        dentist.setLicenseNo(resultSet.getString("license_no"));
        dentist.setContactNumber(resultSet.getString("contact_number"));
        dentist.setActive(resultSet.getBoolean("is_active"));
        return dentist;
    }
}
