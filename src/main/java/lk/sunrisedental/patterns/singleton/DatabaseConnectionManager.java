package lk.sunrisedental.patterns.singleton;

import lk.sunrisedental.exception.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SINGLETON - the application's one and only source of database connections.
 *
 * <p><strong>Why this is genuinely a Singleton and not just a convenient static.</strong> The
 * object owns two things that must exist exactly once per JVM: the JDBC driver registration and a
 * bounded pool of live TCP connections to MySQL. If a second instance existed, the clinic would
 * quietly hold twice its configured connection budget, and the pool limit that protects the
 * database from a request spike would mean nothing. Configuration is also read from disk once here
 * rather than on every data-access call.</p>
 *
 * <p>Implemented with the initialisation-on-demand holder idiom. {@code Holder} is not loaded until
 * {@link #getInstance()} is first called, and the JVM guarantees class initialisation is
 * thread-safe, so this is lazy and correct under concurrency without a single {@code synchronized}
 * keyword or the subtle failure modes of double-checked locking.</p>
 *
 * <p><strong>Connections are borrowed, not owned.</strong> {@link #getConnection()} hands back a
 * proxy whose {@code close()} returns the connection to the pool instead of severing it. Callers
 * therefore use an ordinary try-with-resources block and cannot leak a connection by forgetting
 * which kind they were given.</p>
 */
public final class DatabaseConnectionManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConnectionManager.class.getName());

    private static final String CONFIG_FILE = "/db.properties";

    private static final String KEY_URL         = "sunrise.db.url";
    private static final String KEY_USERNAME    = "sunrise.db.username";
    private static final String KEY_PASSWORD    = "sunrise.db.password";
    private static final String KEY_DRIVER      = "sunrise.db.driver";
    private static final String KEY_POOL_SIZE   = "sunrise.db.pool.size";
    private static final String KEY_POOL_TIMEOUT= "sunrise.db.pool.timeoutSeconds";
    private static final String KEY_VALIDATION  = "sunrise.db.validationSeconds";

    private final String url;
    private final String username;
    private final String password;
    private final int poolSize;
    private final int poolTimeoutSeconds;
    private final int validationSeconds;

    private final BlockingQueue<Connection> idle;
    private final AtomicInteger liveConnections = new AtomicInteger();
    private volatile boolean shutdown;

    /**
     * Lazily initialised, thread-safe holder. Class initialisation is performed once by the JVM
     * under its own lock, which is what makes this idiom correct.
     */
    private static final class Holder {
        private static final DatabaseConnectionManager INSTANCE = new DatabaseConnectionManager();

        private Holder() {
        }
    }

    /** @return the single instance for this JVM. */
    public static DatabaseConnectionManager getInstance() {
        return Holder.INSTANCE;
    }

    private DatabaseConnectionManager() {
        Properties properties = loadProperties();

        this.url = resolve(properties, KEY_URL, null);
        this.username = resolve(properties, KEY_USERNAME, null);
        this.password = resolve(properties, KEY_PASSWORD, null);
        this.poolSize = parsePositiveInt(resolve(properties, KEY_POOL_SIZE, "10"), 10);
        this.poolTimeoutSeconds = parsePositiveInt(resolve(properties, KEY_POOL_TIMEOUT, "10"), 10);
        this.validationSeconds = parsePositiveInt(resolve(properties, KEY_VALIDATION, "2"), 2);
        this.idle = new ArrayBlockingQueue<>(poolSize);

        String driver = resolve(properties, KEY_DRIVER, "com.mysql.cj.jdbc.Driver");
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new DataAccessException("JDBC driver not found on the classpath: " + driver, e);
        }

        if (url == null || username == null) {
            throw new DataAccessException(
                    "Database configuration is incomplete. Copy db.properties.example to "
                    + "db.properties and set " + KEY_URL + " and " + KEY_USERNAME + ".");
        }

        // Checked separately, and with different advice, because the password is the one
        // setting that is deliberately absent from db.properties. Without this the failure
        // would surface as an opaque "Access denied for user" from the driver, which sends
        // people looking for a MySQL problem rather than a missing environment variable.
        if (password == null) {
            throw new DataAccessException(
                    "No database password available. It is intentionally not stored in "
                    + "db.properties; set the SUNRISE_DB_PASSWORD environment variable in the "
                    + "shell that starts Maven or Tomcat.");
        }

        LOGGER.info(() -> "Database connection manager initialised (pool size " + poolSize + ")");
    }

    /**
     * Borrows a connection from the pool.
     *
     * <p>The returned object is a proxy: calling {@code close()} on it returns the underlying
     * connection to the pool. Always use it in a try-with-resources block.</p>
     *
     * @return a usable connection
     * @throws DataAccessException if no connection can be obtained within the configured timeout
     */
    public Connection getConnection() {
        if (shutdown) {
            throw new DataAccessException("The connection pool has been shut down.");
        }
        try {
            Connection connection = idle.poll();

            // Nothing idle: open a new one if we are still under the pool limit.
            if (connection == null && liveConnections.get() < poolSize) {
                connection = openNewConnection();
            }

            // At the limit: wait for another request to hand one back.
            if (connection == null) {
                connection = idle.poll(poolTimeoutSeconds, TimeUnit.SECONDS);
                if (connection == null) {
                    throw new DataAccessException(
                            "Timed out after " + poolTimeoutSeconds
                            + "s waiting for a database connection. All " + poolSize
                            + " connections are in use.");
                }
            }

            // A pooled connection can have been dropped by the server while it sat idle.
            if (!isUsable(connection)) {
                discard(connection);
                connection = openNewConnection();
            }

            return wrap(connection);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessException("Interrupted while waiting for a database connection", e);
        } catch (SQLException e) {
            throw new DataAccessException("Unable to open a database connection", e);
        }
    }

    /**
     * Checks that the database is actually reachable.
     *
     * @return {@code true} if a connection can be obtained and validated right now
     */
    public boolean isDatabaseReachable() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (SQLException | DataAccessException e) {
            LOGGER.log(Level.WARNING, "Database reachability check failed", e);
            return false;
        }
    }

    /** Closes every pooled connection. Called when the web application shuts down. */
    public synchronized void shutdown() {
        shutdown = true;
        Connection connection;
        while ((connection = idle.poll()) != null) {
            closeQuietly(connection);
        }
        liveConnections.set(0);
        LOGGER.info("Database connection pool shut down.");
    }

    /** @return the configured maximum number of simultaneous connections. */
    public int getPoolSize() {
        return poolSize;
    }

    /** @return how many connections are currently open, borrowed or idle. */
    public int getLiveConnectionCount() {
        return liveConnections.get();
    }

    /** @return how many connections are currently sitting idle in the pool. */
    public int getIdleConnectionCount() {
        return idle.size();
    }

    // ------------------------------------------------------------------ internals

    private Connection openNewConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(url, username, password);
        connection.setAutoCommit(true);
        liveConnections.incrementAndGet();
        return connection;
    }

    /**
     * Wraps a real connection so that {@code close()} returns it to the pool.
     *
     * <p>A dynamic proxy is used rather than a hand-written delegating class because
     * {@link Connection} declares around fifty methods, and a hand-written wrapper would have to
     * be revisited every time the JDBC interface grows.</p>
     */
    private Connection wrap(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new PooledConnectionHandler(real));
    }

    private final class PooledConnectionHandler implements InvocationHandler {

        private final Connection real;
        private boolean returned;

        private PooledConnectionHandler(Connection real) {
            this.real = real;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            if ("close".equals(name)) {
                if (!returned) {
                    returned = true;
                    release(real);
                }
                return null;
            }
            if ("isClosed".equals(name)) {
                return returned || real.isClosed();
            }
            if ("toString".equals(name)) {
                return "PooledConnection[" + real + "]";
            }
            if (returned) {
                throw new SQLException("This connection has already been returned to the pool.");
            }
            try {
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                // Unwrap so callers see the real SQLException, not a reflection wrapper.
                throw e.getCause() == null ? e : e.getCause();
            }
        }
    }

    /** Returns a connection to the pool, restoring it to a clean state first. */
    private void release(Connection connection) {
        if (shutdown) {
            discard(connection);
            return;
        }
        try {
            // A caller that began a transaction and neither committed nor rolled back must not
            // hand that transaction on to whoever borrows this connection next.
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
            if (!isUsable(connection) || !idle.offer(connection)) {
                discard(connection);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Discarding a connection that could not be reset", e);
            discard(connection);
        }
    }

    private boolean isUsable(Connection connection) {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(validationSeconds);
        } catch (SQLException e) {
            return false;
        }
    }

    private void discard(Connection connection) {
        closeQuietly(connection);
        liveConnections.decrementAndGet();
    }

    private static void closeQuietly(Connection connection) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Error closing a pooled connection", e);
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream in = DatabaseConnectionManager.class.getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                properties.load(in);
            } else {
                LOGGER.warning(CONFIG_FILE + " not found on the classpath; "
                        + "relying on environment variables.");
            }
        } catch (IOException e) {
            throw new DataAccessException("Unable to read " + CONFIG_FILE, e);
        }
        return properties;
    }

    /**
     * Reads a setting, letting an environment variable win.
     *
     * <p>{@code sunrise.db.password} becomes {@code SUNRISE_DB_PASSWORD}. This is how the CI job
     * supplies its own credentials without a file on disk, and how a real deployment keeps the
     * password out of the WAR entirely.</p>
     */
    private static String resolve(Properties properties, String key, String fallback) {
        String environmentKey = key.toUpperCase().replace('.', '_');
        String value = System.getenv(environmentKey);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            value = properties.getProperty(key);
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
