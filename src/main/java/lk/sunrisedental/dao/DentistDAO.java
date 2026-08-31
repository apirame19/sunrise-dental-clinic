package lk.sunrisedental.dao;

import lk.sunrisedental.model.Dentist;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * Data access for dentists.
 *
 * <p>{@link #findAllActive()} and {@link #findAll()} are deliberately separate. Booking forms must
 * offer only active dentists, but historical reports must still resolve the name of a dentist who
 * has since left - if reports used the active list, last year's figures would lose their author.</p>
 */
public interface DentistDAO {

    /**
     * @param dentistId the primary key
     * @return the dentist, or empty if not found
     */
    Optional<Dentist> findById(int dentistId);

    /** @return dentists currently practising, ordered by name; the booking dropdown. */
    List<Dentist> findAllActive();

    /** @return every dentist including those who have left, ordered by name. */
    List<Dentist> findAll();

    /**
     * @param dentistId the primary key
     * @return {@code true} if that dentist exists and is currently active
     */
    boolean isActive(int dentistId);

    /** @return the number of currently active dentists. */
    long countActive();

    /**
     * @param licenseNo the SLMC registration number
     * @return the dentist holding it, or empty if it is unused
     */
    Optional<Dentist> findByLicenseNo(String licenseNo);

    /**
     * Adds a dentist to the practice.
     *
     * @param dentist the dentist to store
     * @return the generated dentist id
     */
    int insert(Dentist dentist);

    /**
     * Adds a dentist inside an existing transaction.
     *
     * <p>Self-registration writes the login and the roster entry together; a dentist account with
     * no {@code dentists} row could be granted clinical access but never appear on a schedule.</p>
     *
     * @param dentist    the dentist to store
     * @param connection the transactional connection
     * @return the generated dentist id
     */
    int insert(Dentist dentist, Connection connection);

    /**
     * Finds the dentist record a login belongs to.
     *
     * @param userId the account
     * @return the dentist record, or empty if this account is not a dentist's
     */
    Optional<Dentist> findByUserId(int userId);

    /**
     * Binds a dentist record to a login, inside an existing transaction.
     *
     * @param connection the transactional connection
     * @param dentistId  the dentist record
     * @param userId     the account it belongs to
     * @return {@code true} if a row was changed
     */
    boolean linkToUser(Connection connection, int dentistId, int userId);

    /**
     * Updates a dentist's details.
     *
     * <p>The licence number is updatable because a correction to a mistyped registration is a
     * legitimate edit and {@code uk_dentists_license} still stops two dentists sharing one.</p>
     *
     * @param dentist the dentist carrying the new values and an existing id
     * @return {@code true} if a row was changed
     */
    boolean update(Dentist dentist);

    /**
     * Removes a dentist from the booking list, or restores them to it.
     *
     * <p>Never a delete: {@code fk_appointments_dentist} is {@code ON DELETE RESTRICT} precisely so
     * that a departure cannot erase who treated whom.</p>
     *
     * @param dentistId the dentist
     * @param active    {@code true} to make bookable, {@code false} to withdraw
     * @return {@code true} if a row was changed
     */
    boolean setActive(int dentistId, boolean active);
}
