package lk.sunrisedental.service;

import lk.sunrisedental.dao.UserDAO;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.exception.ValidationException;
import lk.sunrisedental.model.Role;
import lk.sunrisedental.model.User;
import lk.sunrisedental.security.PasswordHasher;
import lk.sunrisedental.validation.ValidationResult;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Administration of staff accounts.
 *
 * <p>Two rules here are structural rather than cosmetic, and both exist because the alternative
 * loses data or locks the clinic out of its own system:</p>
 *
 * <ul>
 *   <li><strong>Accounts are deactivated, never deleted.</strong> Every appointment carries
 *       {@code created_by}, and {@code fk_appointments_user} is {@code ON DELETE RESTRICT}. A
 *       deleted account would either be refused by the database or, if the constraint were relaxed,
 *       destroy the record of who booked each visit. {@link #setActive} is therefore the only
 *       removal this system offers.</li>
 *   <li><strong>The last active administrator cannot be deactivated</strong>, and nobody can
 *       deactivate themselves. Either would leave the clinic with no route back into user
 *       management, recoverable only by editing the database by hand.</li>
 * </ul>
 *
 * <p>A plain-text password never leaves this class. It arrives as a {@code char[]}, is handed
 * straight to {@link PasswordHasher} - which wipes it - and only the derived hash is passed to the
 * DAO. Nothing here logs, returns or stores a credential.</p>
 */
public class UserManagementService {

    /** Letters, digits and underscore; a username also appears in the audit trail. */
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.]{2,49}$");

    /** Long enough that the 120,000-iteration cost factor is meaningful. */
    private static final int MIN_PASSWORD_LENGTH = 10;

    private final UserDAO userDAO;

    public UserManagementService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /** @return every staff account, ordered by username; credentials are never displayed. */
    public List<User> findAll() {
        return userDAO.findAll();
    }

    /**
     * @param userId the account
     * @return the account
     * @throws BusinessRuleException if no such account exists
     */
    public User findById(int userId) {
        return userDAO.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("USER_NOT_FOUND",
                        "No staff account was found."));
    }

    /**
     * Creates a staff account.
     *
     * @param username the sign-in name
     * @param fullName the person's name, shown throughout the application
     * @param role     what the account may do
     * @param password the initial password; wiped before this method returns
     * @return the stored account
     * @throws ValidationException if any field is unacceptable; carries every field error at once
     */
    public User create(String username, String fullName, Role role, char[] password) {
        String cleanUsername = trim(username);
        String cleanFullName = trim(fullName);

        ValidationResult result = new ValidationResult();
        validateUsername(cleanUsername, result);
        validateFullName(cleanFullName, result);
        validateRole(role, result);
        validatePassword(password, result);

        if (result.hasErrors()) {
            wipe(password);
            throw new ValidationException(result);
        }

        PasswordHasher.HashedPassword hashed = PasswordHasher.hash(password);

        User user = new User();
        user.setUsername(cleanUsername);
        user.setFullName(cleanFullName);
        user.setRole(role);
        user.setActive(true);
        user.setPasswordHash(hashed.hash());
        user.setPasswordSalt(hashed.salt());
        user.setHashIterations(hashed.iterations());

        user.setUserId(userDAO.insert(user));
        return user;
    }

    /**
     * Changes a staff member's name or role.
     *
     * @param userId   the account to change
     * @param fullName the new name
     * @param role     the new role
     * @param actor    the administrator making the change
     * @return the updated account
     * @throws BusinessRuleException if an administrator would be demoting the last administrator,
     *                               or themselves
     */
    public User update(int userId, String fullName, Role role, User actor) {
        User existing = findById(userId);
        String cleanFullName = trim(fullName);

        ValidationResult result = new ValidationResult();
        validateFullName(cleanFullName, result);
        validateRole(role, result);
        if (result.hasErrors()) {
            throw new ValidationException(result);
        }

        // Demoting the last administrator has exactly the same effect as deactivating them.
        if (existing.getRole() == Role.ADMIN && role != Role.ADMIN) {
            requireAnotherAdministratorRemains(existing, actor,
                    "the only administrator, so their role cannot be changed");
        }

        existing.setFullName(cleanFullName);
        existing.setRole(role);

        if (!userDAO.updateProfile(existing)) {
            throw new BusinessRuleException("USER_UPDATE_FAILED",
                    "The staff account could not be updated.");
        }
        return existing;
    }

    /**
     * Deactivates or reactivates an account.
     *
     * @param userId the account
     * @param active {@code true} to allow sign-in again, {@code false} to refuse it
     * @param actor  the administrator making the change
     * @return the updated account
     * @throws BusinessRuleException if this would leave nobody able to administer the system
     */
    public User setActive(int userId, boolean active, User actor) {
        User existing = findById(userId);

        if (!active) {
            if (actor != null && actor.getUserId() == userId) {
                throw new BusinessRuleException("CANNOT_DEACTIVATE_SELF",
                        "You cannot deactivate the account you are signed in with.");
            }
            if (existing.getRole() == Role.ADMIN) {
                requireAnotherAdministratorRemains(existing, actor,
                        "the only active administrator, so this account cannot be deactivated");
            }
        }

        if (existing.isActive() == active) {
            return existing;
        }

        if (!userDAO.setActive(userId, active)) {
            throw new BusinessRuleException("USER_UPDATE_FAILED",
                    "The staff account could not be updated.");
        }
        existing.setActive(active);
        return existing;
    }

    /**
     * Replaces an account's password and clears any lockout.
     *
     * @param userId      the account
     * @param newPassword the new password; wiped before this method returns
     * @return the account
     */
    public User resetPassword(int userId, char[] newPassword) {
        User existing = findById(userId);

        ValidationResult result = new ValidationResult();
        validatePassword(newPassword, result);
        if (result.hasErrors()) {
            wipe(newPassword);
            throw new ValidationException(result);
        }

        PasswordHasher.HashedPassword hashed = PasswordHasher.hash(newPassword);

        if (!userDAO.updatePassword(userId, hashed.hash(), hashed.salt(), hashed.iterations())) {
            throw new BusinessRuleException("PASSWORD_RESET_FAILED",
                    "The password could not be changed.");
        }
        existing.setFailedLoginAttempts(0);
        existing.setLockedUntil(null);
        return existing;
    }

    // ------------------------------------------------------------------ rules

    /**
     * Refuses a change that would remove the system's last way in.
     *
     * @param target the administrator being changed
     * @param actor  who is making the change, used only for the message
     * @param what   the tail of the sentence shown to the user
     */
    private void requireAnotherAdministratorRemains(User target, User actor, String what) {
        long activeAdmins = userDAO.countActiveByRole(Role.ADMIN.name());
        boolean targetCounts = target.isActive();

        if (!targetCounts || activeAdmins > 1) {
            return;
        }
        String who = actor != null && actor.getUserId() == target.getUserId()
                ? "You are"
                : target.getFullName() + " is";
        throw new BusinessRuleException("LAST_ADMINISTRATOR", who + " " + what + ".");
    }

    private void validateUsername(String username, ValidationResult result) {
        if (username == null) {
            result.addError("username", "Please enter a username.");
            return;
        }
        if (!USERNAME.matcher(username).matches()) {
            result.addError("username",
                    "A username must start with a letter and be 3 to 50 characters of letters, "
                    + "digits, dots or underscores.");
            return;
        }
        if (userDAO.existsByUsername(username)) {
            result.addError("username",
                    "That username is already taken. Please choose another.");
        }
    }

    private static void validateFullName(String fullName, ValidationResult result) {
        if (fullName == null || fullName.length() < 2) {
            result.addError("fullName", "Please enter the staff member's full name.");
        } else if (fullName.length() > 100) {
            result.addError("fullName", "The full name may be at most 100 characters.");
        }
    }

    /**
     * Checks the role an administrator chose.
     *
     * <p>{@link Role#PATIENT} is refused here as well as being absent from the dropdown. The
     * dropdown is a convenience for the administrator; this is the check that actually holds,
     * because a submitted role arrives as text in a request parameter and a form is trivially
     * altered before it is sent. A patient account exists only through self-registration, where it
     * is bound to a {@code patients} record - one minted from this screen would carry a patient's
     * authority with no record to scope it to.</p>
     */
    private static void validateRole(Role role, ValidationResult result) {
        if (role == null) {
            result.addError("role", "Please choose a role for this account.");
            return;
        }
        if (!role.isAssignableByAdministrator()) {
            result.addError("role",
                    "A patient account cannot be created here. Patients register themselves.");
        }
    }

    /**
     * Checks the password without ever copying it into a String, which would leave an immutable
     * plain-text credential on the heap until garbage collection got round to it.
     */
    private static void validatePassword(char[] password, ValidationResult result) {
        if (password == null || password.length == 0) {
            result.addError("password", "Please enter a password.");
            return;
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            result.addError("password",
                    "The password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
            return;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        boolean hasOther = false;
        for (char c : password) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasOther = true;
            }
        }
        if (!hasLetter || !hasDigit || !hasOther) {
            result.addError("password",
                    "The password must contain letters, digits and at least one other character.");
        }
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
