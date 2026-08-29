package lk.sunrisedental.service;

import lk.sunrisedental.dao.UserDAO;
import lk.sunrisedental.exception.AuthenticationException;
import lk.sunrisedental.model.User;
import lk.sunrisedental.patterns.singleton.ConfigurationManager;
import lk.sunrisedental.security.PasswordHasher;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Verifies staff credentials and maintains the sign-in audit trail.
 *
 * <p>Three properties are deliberate and worth stating, because each is a place this could quietly
 * be got wrong:</p>
 *
 * <ul>
 *   <li><strong>Every failure looks identical to the user.</strong> Unknown username, wrong
 *       password and deactivated account all produce the same message. Distinguishing them would
 *       turn the login form into a way of confirming which staff usernames exist.</li>
 *   <li><strong>The password is verified even when the username is unknown.</strong> Returning
 *       early would make a wrong username measurably faster than a wrong password, and that timing
 *       difference is enough to enumerate accounts. A dummy verification keeps the two paths
 *       comparable.</li>
 *   <li><strong>The clock is injected.</strong> Lockout expiry cannot be tested by waiting fifteen
 *       minutes.</li>
 * </ul>
 *
 * <p>This class imports nothing from {@code jakarta.servlet}. Sessions are the controller's
 * concern; this answers only "are these credentials good".</p>
 */
public class AuthenticationService {

    /**
     * A hash used only to keep the timing of a failed lookup comparable to a real verification.
     * Its value is irrelevant - what matters is that PBKDF2 runs either way.
     */
    private static final String DUMMY_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String DUMMY_SALT = "AAAAAAAAAAAAAAAAAAAAAA==";

    private final UserDAO userDAO;
    private final ConfigurationManager configuration;
    private final Clock clock;

    /**
     * @param userDAO       account and audit storage
     * @param configuration supplies the lockout threshold and duration
     * @param clock         defines "now" for lockout expiry; injected so the rule is testable
     */
    public AuthenticationService(UserDAO userDAO, ConfigurationManager configuration, Clock clock) {
        this.userDAO = userDAO;
        this.configuration = configuration;
        this.clock = clock;
    }

    /**
     * Verifies a username and password.
     *
     * @param username  the submitted username
     * @param password  the submitted password; wiped before this method returns
     * @param ipAddress the client address for the audit trail, or null
     * @return the authenticated user
     * @throws AuthenticationException if the credentials are not valid, the account is inactive,
     *                                 or the account is locked
     */
    public User authenticate(String username, char[] password, String ipAddress) {
        String submitted = username == null ? "" : username.trim();

        if (submitted.isEmpty() || password == null || password.length == 0) {
            wipe(password);
            userDAO.recordLoginAttempt(submitted, false, ipAddress, "Missing credentials");
            throw AuthenticationException.invalidCredentials("MISSING_CREDENTIALS");
        }

        Optional<User> found = userDAO.findByUsername(submitted);

        if (found.isEmpty()) {
            // Run a verification anyway so an unknown username takes the same time as a known one.
            PasswordHasher.matches(password, DUMMY_HASH, DUMMY_SALT,
                    PasswordHasher.DEFAULT_ITERATIONS);
            userDAO.recordLoginAttempt(submitted, false, ipAddress, "Unknown username");
            throw AuthenticationException.invalidCredentials("UNKNOWN_USERNAME");
        }

        User user = found.get();

        if (isLocked(user)) {
            wipe(password);
            userDAO.recordLoginAttempt(submitted, false, ipAddress, "Account locked");
            throw AuthenticationException.accountLocked(minutesUntilUnlock(user));
        }

        boolean correct = PasswordHasher.matches(
                password, user.getPasswordHash(), user.getPasswordSalt(), user.getHashIterations());

        if (!correct) {
            registerFailure(user, ipAddress);
            throw AuthenticationException.invalidCredentials("BAD_PASSWORD");
        }

        // Checked only after the password is verified. Telling someone their account is
        // deactivated before they prove who they are would confirm the username exists.
        if (!user.isActive()) {
            userDAO.recordLoginAttempt(submitted, false, ipAddress, "Account deactivated");
            throw AuthenticationException.invalidCredentials("ACCOUNT_INACTIVE");
        }

        userDAO.recordSuccessfulLogin(user.getUserId());
        userDAO.recordLoginAttempt(submitted, true, ipAddress, null);

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return user;
    }

    /** @return {@code true} if the account is locked out at this moment. */
    private boolean isLocked(User user) {
        LocalDateTime lockedUntil = user.getLockedUntil();
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now(clock));
    }

    /** @return whole minutes remaining on the lock, at least one so the message is never "0". */
    private int minutesUntilUnlock(User user) {
        Duration remaining = Duration.between(LocalDateTime.now(clock), user.getLockedUntil());
        return Math.max(1, (int) Math.ceil(remaining.toSeconds() / 60.0));
    }

    /**
     * Records a wrong password and locks the account once the threshold is reached.
     *
     * <p>The counter is persisted rather than held in memory, so restarting Tomcat does not hand an
     * attacker a fresh set of attempts.</p>
     */
    private void registerFailure(User user, String ipAddress) {
        int attempts = user.getFailedLoginAttempts() + 1;
        int threshold = configuration.getMaxLoginAttempts();

        LocalDateTime lockedUntil = attempts >= threshold
                ? LocalDateTime.now(clock).plusMinutes(configuration.getLockoutMinutes())
                : null;

        userDAO.recordFailedLogin(user.getUserId(), attempts, lockedUntil);
        userDAO.recordLoginAttempt(user.getUsername(), false, ipAddress,
                lockedUntil == null
                        ? "Bad password (" + attempts + " of " + threshold + ")"
                        : "Bad password - account locked");
    }

    private static void wipe(char[] password) {
        if (password != null) {
            java.util.Arrays.fill(password, '\0');
        }
    }
}
