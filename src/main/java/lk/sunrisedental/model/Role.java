package lk.sunrisedental.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Roles, each carrying the authority it grants.
 *
 * <p>Expressing permissions as methods on the role, rather than as {@code if (role.equals("ADMIN"))}
 * checks spread across servlets, means a change to who may see revenue figures happens in exactly
 * one place. The authorisation filter and the navigation menu both ask the same questions here, so
 * a menu item can never appear for a user who would be refused on clicking it.</p>
 *
 * <p><strong>Three of these are staff; {@link #PATIENT} is not.</strong> That distinction matters
 * more than it looks. Every predicate below that used to return an unconditional {@code true} now
 * excludes patients explicitly, because the authorisation filter permits any path it does not list
 * - a sensible default for staff pages, and a data breach if a patient inherits it. A patient is a
 * member of the public who may see their own appointments and nothing else, so
 * {@code AuthorizationFilter} gives that one role an allowlist instead.</p>
 */
public enum Role {

    /** Manages the clinic: full access including financial reports and master data. */
    ADMIN("Administrator"),

    /** Front desk: books, searches, bills and runs operational reports. */
    RECEPTIONIST("Receptionist"),

    /** Clinical staff: sees schedules and patient history, records visit outcomes. */
    DENTIST("Dentist"),

    /**
     * A member of the public with a login: may see their own appointments and their own details,
     * and nothing belonging to anybody else. Never granted by an administrator - the only way to
     * hold this role is to have self-registered.
     */
    PATIENT("Patient");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** @return a human-readable label for display in the user interface. */
    public String getLabel() {
        return label;
    }

    /**
     * @return {@code true} if this role may view revenue and billing-summary reports.
     *         Restricted to administrators: takings are commercially sensitive and the front desk
     *         has no operational need for them.
     */
    public boolean canViewFinancialReports() {
        return this == ADMIN;
    }

    /** @return {@code true} if this role may add or amend dentists, treatments and prices. */
    public boolean canManageMasterData() {
        return this == ADMIN;
    }

    /** @return {@code true} if this role may create appointments and register patients. */
    public boolean canRegisterAppointments() {
        return this == ADMIN || this == RECEPTIONIST;
    }

    /** @return {@code true} if this role may generate bills. */
    public boolean canGenerateBills() {
        return this == ADMIN || this == RECEPTIONIST;
    }

    /**
     * @return {@code true} if this role may record a visit outcome (completed, no-show).
     *         Every member of staff may; a patient may not mark their own appointment attended.
     */
    public boolean canUpdateAppointmentStatus() {
        return isStaff();
    }

    /**
     * @return {@code true} if this role may view operational reports and the clinic dashboard.
     *         Staff only - these list every patient booked that day.
     */
    public boolean canViewOperationalReports() {
        return isStaff();
    }

    /**
     * @return {@code true} for a role that works at the clinic, as opposed to a member of the
     *         public holding a patient login.
     *
     *         <p>This is the question the authorisation filter asks before applying the ordinary
     *         staff rules, so a role added later is refused the staff area until somebody decides
     *         otherwise, rather than being admitted by an unconditional {@code true} nobody
     *         revisited.</p>
     */
    public boolean isStaff() {
        return this != PATIENT;
    }

    /**
     * @return {@code true} if an administrator may assign this role when creating or amending a
     *         staff account.
     *
     *         <p>{@code PATIENT} is excluded deliberately. A patient account exists only through
     *         self-registration, where it is bound to a {@code patients} record; one minted from
     *         the staff screen would have a patient's authority and no patient record to scope it
     *         to, and every "show me my appointments" query would have nothing to match on.</p>
     */
    public boolean isAssignableByAdministrator() {
        return this != PATIENT;
    }

    /** @return the roles an administrator may choose from, in the staff-account screens. */
    public static Role[] assignableByAdministrator() {
        return Arrays.stream(values())
                .filter(Role::isAssignableByAdministrator)
                .toArray(Role[]::new);
    }

    /**
     * Parses a role from stored or submitted text without throwing.
     *
     * @param value the candidate name, in any case, possibly padded or null
     * @return the matching role, or empty if the value is not recognised
     */
    public static Optional<Role> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalised = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(role -> role.name().equals(normalised))
                .findFirst();
    }
}
