package lk.sunrisedental.dao.impl;

import lk.sunrisedental.dao.SqlErrorTranslator;
import lk.sunrisedental.dao.UserDAO;
import lk.sunrisedental.model.Role;
import lk.sunrisedental.model.User;
import lk.sunrisedental.patterns.singleton.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO - plain JDBC data access for staff accounts and the sign-in audit trail.
 *
 * <p>Every method here follows the same three steps, written out in full so the database work is
 * visible: borrow a {@link Connection} from the {@link DatabaseConnectionManager} singleton,
 * prepare a {@link PreparedStatement}, then read a {@link ResultSet}. Everything is inside
 * try-with-resources, so each of those is closed on every path including exceptions.</p>
 *
 * <p>Values are always bound with {@code statement.setX(...)} and never concatenated into the SQL
 * text. That is what makes SQL injection impossible here rather than merely unlikely.</p>
 *
 * <p>Note what this class never sees: a plaintext password. The service layer derives the hash
 * with {@code PasswordHasher} and this DAO only moves the stored hash, salt and iteration count in
 * and out of MySQL.</p>
 */
public class JdbcUserDAO implements UserDAO {

    private static final String COLUMNS =
            "user_id, username, password_hash, password_salt, hash_iterations, full_name, role, "
            + "is_active, failed_login_attempts, locked_until, last_login_at, created_at";

    private static final String SELECT_BY_USERNAME =
            "SELECT " + COLUMNS + " FROM users WHERE username = ?";

    private static final String SELECT_BY_ID =
            "SELECT " + COLUMNS + " FROM users WHERE user_id = ?";

    private static final String SELECT_ALL =
            "SELECT " + COLUMNS + " FROM users ORDER BY username";

    private static final String EXISTS_USERNAME =
            "SELECT COUNT(*) FROM users WHERE username = ?";

    private static final String INSERT =
            "INSERT INTO users (username, password_hash, password_salt, hash_iterations, "
            + "full_name, role, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_PROFILE =
            "UPDATE users SET full_name = ?, role = ? WHERE user_id = ?";

    private static final String SET_ACTIVE =
            "UPDATE users SET is_active = ? WHERE user_id = ?";

    private static final String UPDATE_PASSWORD =
            "UPDATE users SET password_hash = ?, password_salt = ?, hash_iterations = ?, "
            + "failed_login_attempts = 0, locked_until = NULL WHERE user_id = ?";

    private static final String COUNT_ACTIVE_BY_ROLE =
            "SELECT COUNT(*) FROM users WHERE role = ? AND is_active = 1";

    private static final String RECORD_SUCCESS =
            "UPDATE users SET last_login_at = CURRENT_TIMESTAMP, failed_login_attempts = 0, "
            + "locked_until = NULL WHERE user_id = ?";

    private static final String RECORD_FAILURE =
            "UPDATE users SET failed_login_attempts = ?, locked_until = ? WHERE user_id = ?";

    private static final String INSERT_AUDIT =
            "INSERT INTO login_audit (username, success, ip_address, failure_note) "
            + "VALUES (?, ?, ?, ?)";

    private static final String COUNT_RECENT_FAILURES =
            "SELECT COUNT(*) FROM login_audit "
            + "WHERE username = ? AND success = 0 AND attempted_at >= ?";

    /** SINGLETON - the application's one source of MySQL connections. */
    private final DatabaseConnectionManager connectionManager;

    public JdbcUserDAO() {
        this(DatabaseConnectionManager.getInstance());
    }

    /**
     * @param connectionManager the source of connections; injectable so a test can point this DAO
     *                          at a test schema
     */
    public JdbcUserDAO(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // ------------------------------------------------------------------ reads

    @Override
    public Optional<User> findByUsername(String username) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_USERNAME)) {

            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapUser(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading user by username");
        }
    }

    @Override
    public Optional<User> findById(int userId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {

            statement.setInt(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapUser(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading user " + userId);
        }
    }

    @Override
    public List<User> findAll() {
        List<User> users = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
            return users;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Listing users");
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_USERNAME)) {

            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Checking whether a username is taken");
        }
    }

    @Override
    public long countActiveByRole(String role) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_ACTIVE_BY_ROLE)) {

            statement.setString(1, role);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Counting active accounts with role " + role);
        }
    }

    // ------------------------------------------------------------------ writes

    @Override
    public int insert(User user) {
        try (Connection connection = connectionManager.getConnection()) {
            return insert(user, connection);
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Creating staff account");
        }
    }

    /**
     * Inserts an account on the caller's transactional connection.
     *
     * <p>The connection is NOT closed here - the caller owns it. Only the statement and the
     * generated-keys result set are closed.</p>
     */
    @Override
    public int insert(User user, Connection connection) {
        try (PreparedStatement statement =
                     connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, user.getPasswordSalt());
            statement.setInt(4, user.getHashIterations());
            statement.setString(5, user.getFullName());
            statement.setString(6, user.getRole().name());
            statement.setBoolean(7, user.isActive());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new SQLException("Creating an account returned no generated key");
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Creating staff account");
        }
    }

    @Override
    public boolean updateProfile(User user) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_PROFILE)) {

            statement.setString(1, user.getFullName());
            statement.setString(2, user.getRole().name());
            statement.setInt(3, user.getUserId());

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Updating staff account " + user.getUserId());
        }
    }

    @Override
    public boolean setActive(int userId, boolean active) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SET_ACTIVE)) {

            statement.setBoolean(1, active);
            statement.setInt(2, userId);

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    (active ? "Reactivating" : "Deactivating") + " staff account " + userId);
        }
    }

    @Override
    public boolean updatePassword(int userId, String hash, String salt, int iterations) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_PASSWORD)) {

            statement.setString(1, hash);
            statement.setString(2, salt);
            statement.setInt(3, iterations);
            statement.setInt(4, userId);

            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Resetting the password for staff account " + userId);
        }
    }

    // ------------------------------------------------------------------ sign-in bookkeeping

    @Override
    public void recordSuccessfulLogin(int userId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(RECORD_SUCCESS)) {

            statement.setInt(1, userId);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Recording successful sign-in");
        }
    }

    @Override
    public void recordFailedLogin(int userId, int attempts, LocalDateTime lockedUntil) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(RECORD_FAILURE)) {

            statement.setInt(1, attempts);
            if (lockedUntil == null) {
                statement.setNull(2, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(2, Timestamp.valueOf(lockedUntil));
            }
            statement.setInt(3, userId);

            statement.executeUpdate();

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Recording failed sign-in");
        }
    }

    @Override
    public void recordLoginAttempt(String username, boolean success, String ipAddress,
                                   String failureNote) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_AUDIT)) {

            statement.setString(1, username);
            statement.setBoolean(2, success);
            statement.setString(3, ipAddress);
            statement.setString(4, failureNote);

            statement.executeUpdate();

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Writing sign-in audit record");
        }
    }

    @Override
    public int countFailedAttemptsSince(String username, LocalDateTime since) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_RECENT_FAILURES)) {

            statement.setString(1, username);
            statement.setTimestamp(2, Timestamp.valueOf(since));

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Counting recent failed sign-ins");
        }
    }

    // ------------------------------------------------------------------ row mapping

    /**
     * Copies the current row of {@code users} into a {@link User}.
     *
     * <p>Timestamps are read with {@link ResultSet#getObject(String, Class)}, which returns a
     * {@code java.time} value directly and yields {@code null} for SQL NULL - avoiding both the
     * legacy {@code java.util.Date} conversions and the {@code wasNull()} dance.</p>
     */
    private static User mapUser(ResultSet resultSet) throws SQLException {
        User user = new User();
        user.setUserId(resultSet.getInt("user_id"));
        user.setUsername(resultSet.getString("username"));
        user.setPasswordHash(resultSet.getString("password_hash"));
        user.setPasswordSalt(resultSet.getString("password_salt"));
        user.setHashIterations(resultSet.getInt("hash_iterations"));
        user.setFullName(resultSet.getString("full_name"));
        user.setRole(Role.fromString(resultSet.getString("role")).orElse(Role.RECEPTIONIST));
        user.setActive(resultSet.getBoolean("is_active"));
        user.setFailedLoginAttempts(resultSet.getInt("failed_login_attempts"));
        user.setLockedUntil(resultSet.getObject("locked_until", LocalDateTime.class));
        user.setLastLoginAt(resultSet.getObject("last_login_at", LocalDateTime.class));
        user.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));
        return user;
    }
}
