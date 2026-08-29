package lk.sunrisedental.dao;

import lk.sunrisedental.model.User;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data access for staff accounts and the sign-in audit trail.
 *
 * <p>An interface rather than a concrete class so that {@code AuthenticationService} can be tested
 * against a mock. Authentication has a lot of branches worth testing - unknown user, wrong
 * password, deactivated account, locked account, lock expiry - and none of them should need a
 * running MySQL to exercise.</p>
 *
 * <p>Note what is absent: there is no method that takes a password. Credential comparison happens
 * in the service layer using {@code PasswordHasher}; the DAO only ever moves stored hash material
 * in and out.</p>
 */
public interface UserDAO {

    /**
     * @param username the sign-in name; matched exactly
     * @return the account, or empty if no such username exists
     */
    Optional<User> findByUsername(String username);

    /**
     * @param userId the primary key
     * @return the account, or empty if it does not exist
     */
    Optional<User> findById(int userId);

    /** @return every account, ordered by username. */
    List<User> findAll();

    /**
     * @param username the sign-in name to test; matched exactly
     * @return {@code true} if that username is already taken
     */
    boolean existsByUsername(String username);

    /**
     * Creates a staff account.
     *
     * @param user the account, carrying its already-derived hash, salt and iteration count
     * @return the generated user id
     */
    int insert(User user);

    /**
     * Creates a staff or patient account inside an existing transaction.
     *
     * <p>Self-registration writes two rows that only make sense together: the login and the
     * {@code patients} or {@code dentists} record it belongs to. Committing the account without
     * its record would leave someone able to sign in with nothing to show them.</p>
     *
     * @param user       the account, carrying its already-derived hash, salt and iteration count
     * @param connection the transactional connection
     * @return the generated user id
     */
    int insert(User user, Connection connection);

    /**
     * Updates the parts of an account an administrator may change: full name and role.
     *
     * <p>The username is deliberately not updatable. It is what the sign-in audit trail records,
     * and renaming an account would silently detach it from its own history.</p>
     *
     * @param user the account carrying the new values and an existing id
     * @return {@code true} if a row was changed
     */
    boolean updateProfile(User user);

    /**
     * Activates or deactivates an account.
     *
     * <p>Accounts are never deleted. {@code created_by} on every appointment references
     * {@code users}, so removing a staff member would either orphan or destroy the record of who
     * booked each visit.</p>
     *
     * @param userId the account
     * @param active {@code true} to allow sign-in, {@code false} to refuse it
     * @return {@code true} if a row was changed
     */
    boolean setActive(int userId, boolean active);

    /**
     * Replaces an account's stored credential and clears any lock.
     *
     * @param userId     the account
     * @param hash       Base64 of the new derived key
     * @param salt       Base64 of the new salt
     * @param iterations the cost factor used
     * @return {@code true} if a row was changed
     */
    boolean updatePassword(int userId, String hash, String salt, int iterations);

    /**
     * @param role the role to count
     * @return how many active accounts currently hold that role
     */
    long countActiveByRole(String role);

    /**
     * Records a successful sign-in: stamps {@code last_login_at} and clears the failure counter
     * and any lock.
     *
     * @param userId the account that signed in
     */
    void recordSuccessfulLogin(int userId);

    /**
     * Records a failed sign-in attempt against an existing account.
     *
     * @param userId       the account
     * @param attempts     the new failure count
     * @param lockedUntil  when the lock expires, or {@code null} if the threshold is not reached
     */
    void recordFailedLogin(int userId, int attempts, LocalDateTime lockedUntil);

    /**
     * Appends to the sign-in audit trail.
     *
     * <p>Written for failures against usernames that do not exist as well as for real accounts,
     * because an attacker guessing usernames is exactly what such a trail needs to show.</p>
     *
     * @param username     the name that was submitted
     * @param success      whether the attempt succeeded
     * @param ipAddress    the client address, or {@code null} if unavailable
     * @param failureNote  short internal reason; never shown to the user
     */
    void recordLoginAttempt(String username, boolean success, String ipAddress, String failureNote);

    /**
     * @param username the name that was submitted
     * @param since    the start of the window to count within
     * @return how many failed attempts that username has accumulated since {@code since}
     */
    int countFailedAttemptsSince(String username, LocalDateTime since);
}
