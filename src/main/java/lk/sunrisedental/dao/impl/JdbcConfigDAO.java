package lk.sunrisedental.dao.impl;

import lk.sunrisedental.dao.ConfigDAO;
import lk.sunrisedental.dao.SqlErrorTranslator;
import lk.sunrisedental.patterns.singleton.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DAO - plain JDBC access to the {@code app_config} settings table.
 *
 * <p>The one method here follows the same visible steps as every other DAO: borrow a
 * {@link Connection} from the {@link DatabaseConnectionManager} singleton, prepare a
 * {@link PreparedStatement}, then read a {@link ResultSet}, all inside try-with-resources.</p>
 *
 * <p>Settings are read in bulk rather than key by key, because {@code ConfigurationManager} caches
 * the whole set once. The consultation fee, tax rate and opening hours are consulted on every bill
 * and every availability check; a query per key would put the busiest lookups in the system on the
 * slowest path for no benefit.</p>
 */
public class JdbcConfigDAO implements ConfigDAO {

    private static final String SELECT_ALL =
            "SELECT config_key, config_value FROM app_config ORDER BY config_key";

    /** SINGLETON - the application's one source of MySQL connections. */
    private final DatabaseConnectionManager connectionManager;

    public JdbcConfigDAO() {
        this(DatabaseConnectionManager.getInstance());
    }

    /**
     * @param connectionManager the source of connections; injectable so a test can point this DAO
     *                          at a test schema
     */
    public JdbcConfigDAO(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Reads every row of {@code app_config}.
     *
     * <p>A {@link LinkedHashMap} is used so the settings keep the {@code ORDER BY config_key}
     * order the database returned them in, which is what makes an administrative listing of the
     * settings predictable rather than arbitrary.</p>
     *
     * @return every setting, keyed by {@code config_key}
     */
    @Override
    public Map<String, String> loadAll() {
        Map<String, String> settings = new LinkedHashMap<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                settings.put(resultSet.getString("config_key"),
                        resultSet.getString("config_value"));
            }
            return settings;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading application configuration");
        }
    }
}
