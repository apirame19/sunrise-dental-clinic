package lk.sunrisedental.dao;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs several data-access operations as one unit of work.
 *
 * <p>Transaction boundaries belong to the service layer, because only the service knows that
 * registering a patient and booking their first appointment are one business action. But the
 * service must not import {@code java.sql} to express that, or the layer separation is gone. This
 * interface is the compromise: the service says "these things happen together", and the
 * implementation deals with connections, commit and rollback.</p>
 *
 * <p>It is an interface rather than a class so that a service can be unit-tested with a
 * transaction manager that simply runs the callback, letting mocked DAOs stand in for the real
 * ones with no database anywhere in the test.</p>
 */
public interface TransactionManager {

    /**
     * Runs the given work in a transaction, committing on success and rolling back on any failure.
     *
     * @param work what to do
     * @param <T>  the result type
     * @return whatever the work returns
     */
    <T> T execute(TransactionalWork<T> work);

    /**
     * A unit of work performed against a transactional connection.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    interface TransactionalWork<T> {
        T execute(Connection connection) throws SQLException;
    }
}
