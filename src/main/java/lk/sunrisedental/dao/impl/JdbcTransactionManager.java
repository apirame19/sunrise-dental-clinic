package lk.sunrisedental.dao.impl;

import lk.sunrisedental.dao.SqlErrorTranslator;
import lk.sunrisedental.dao.TransactionManager;
import lk.sunrisedental.patterns.singleton.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC implementation of {@link TransactionManager}.
 *
 * <p>Borrows a connection, turns off auto-commit, runs the work, and commits. Anything thrown -
 * a {@link SQLException} or a business rule rejection from deeper in the call - rolls the whole
 * unit back before propagating.</p>
 *
 * <p>Auto-commit is restored in the {@code finally} block before the connection returns to the
 * pool. Without that, the next request to borrow this connection would inherit an open
 * transaction, and its writes would sit uncommitted until something unrelated happened to commit
 * them. The pool guards against this as well; doing it in both places is cheap and the failure
 * mode is nasty enough to be worth guarding twice.</p>
 */
public class JdbcTransactionManager implements TransactionManager {

    private static final Logger LOGGER = Logger.getLogger(JdbcTransactionManager.class.getName());

    private final DatabaseConnectionManager connectionManager;

    public JdbcTransactionManager() {
        this(DatabaseConnectionManager.getInstance());
    }

    public JdbcTransactionManager(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public <T> T execute(TransactionalWork<T> work) {
        Connection connection = null;
        try {
            connection = connectionManager.getConnection();
            connection.setAutoCommit(false);

            T result = work.execute(connection);

            connection.commit();
            return result;

        } catch (SQLException e) {
            rollback(connection);
            throw SqlErrorTranslator.translate(e, "Transaction failed");

        } catch (RuntimeException e) {
            // A business rule rejected the work part-way through. Everything already written in
            // this unit must go, or the clinic is left with, say, a patient and no appointment.
            rollback(connection);
            throw e;

        } finally {
            close(connection);
        }
    }

    private static void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException e) {
            // Already failing; the original exception is the one worth propagating.
            LOGGER.log(Level.WARNING, "Rollback failed", e);
        }
    }

    private static void close(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Could not restore auto-commit", e);
        }
        try {
            connection.close();
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Could not return connection to the pool", e);
        }
    }
}
