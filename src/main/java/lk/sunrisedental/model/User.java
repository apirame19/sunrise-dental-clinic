package lk.sunrisedental.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A staff account.
 *
 * <p>Carries the stored credential material ({@code passwordHash}, {@code passwordSalt},
 * {@code hashIterations}) because the data-access layer must read and write it, but
 * {@link #toString()} deliberately omits all three. A {@code User} ends up in log lines and
 * debugger views, and a hash printed into a log file is a credential leak.</p>
 *
 * <p>There is no plain-text password field anywhere on this class, so there is nowhere for one to
 * be accidentally stored.</p>
 */
public class User {

    private int userId;
    private String username;
    private String passwordHash;
    private String passwordSalt;
    private int hashIterations;
    private String fullName;
    private Role role;
    private boolean active = true;
    private int failedLoginAttempts;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    public User() {
    }

    public User(int userId, String username, String fullName, Role role) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }

    /**
     * @return {@code true} if the account is currently locked out because of repeated failed
     *         sign-in attempts. A lock that has expired is not a lock.
     */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    /** @return {@code true} if this account is permitted to sign in right now. */
    public boolean canSignIn() {
        return active && !isLocked();
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public int getHashIterations() {
        return hashIterations;
    }

    public void setHashIterations(int hashIterations) {
        this.hashIterations = hashIterations;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }
        return userId != 0 && userId == user.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    /** Credential fields are intentionally excluded - see the class comment. */
    @Override
    public String toString() {
        return "User{userId=" + userId
                + ", username='" + username + '\''
                + ", fullName='" + fullName + '\''
                + ", role=" + role
                + ", active=" + active + '}';
    }
}
