package lk.sunrisedental.controller;

import lk.sunrisedental.model.Role;
import lk.sunrisedental.patterns.composite.MenuComponent;

/**
 * The application's navigation tree, declared once.
 *
 * <p>This is the second genuine use of the Composite pattern, alongside reports. The menu is a real
 * tree - <em>Reports</em> has four children, <em>Clinic records</em> has three - and one recursive
 * JSP fragment renders it whatever its shape.</p>
 *
 * <p><strong>The predicates below are the same ones the facade enforces.</strong> "Revenue report"
 * is visible to a role exactly when {@code Role.canViewFinancialReports()} is true, and that is the
 * same method {@code ClinicManagementFacade.report} calls before running it. The menu and the
 * authorisation rule cannot drift apart, because there is only one rule; a receptionist is never
 * shown a link that would refuse them.</p>
 *
 * <p>{@link MenuComponent#filterFor(Role)} prunes the tree per role and removes any group left
 * empty, so nobody sees a heading that leads nowhere.</p>
 */
public final class Navigation {

    private Navigation() {
    }

    /**
     * Builds the full menu and prunes it to what one role may use.
     *
     * @param role the signed-in user's role
     * @return the pruned tree, or null if the role may see nothing at all
     */
    public static MenuComponent forRole(Role role) {
        // A patient gets a different tree, not a filtered version of the staff one. Pruning the
        // staff menu would rely on every single item carrying the right predicate, and an item
        // added later without one would appear in a patient's menu. Building separately means a
        // new staff page cannot leak into the patient's navigation by omission.
        if (role == Role.PATIENT) {
            return buildPatientMenu().filterFor(role);
        }
        return build().filterFor(role);
    }

    /**
     * @return the patient's own menu: their appointments, and help. It deliberately mirrors the
     *         allowlist in {@code AuthorizationFilter} - a patient is never shown a link that the
     *         filter would refuse.
     */
    static MenuComponent buildPatientMenu() {
        MenuComponent root = MenuComponent.group("Sunrise Dental Clinic", "");

        root.add(MenuComponent.item("My appointments", "/app/my/appointments", "⚑",
                role -> role == Role.PATIENT));
        root.add(MenuComponent.item("Help", "/app/help", "?", role -> true));

        return root;
    }

    /** @return the complete staff menu, before any role filtering. */
    static MenuComponent build() {
        MenuComponent root = MenuComponent.group("Sunrise Dental Clinic", "");

        root.add(MenuComponent.item("Dashboard", "/app/dashboard", "▦",
                Role::canViewOperationalReports));

        MenuComponent appointments = MenuComponent.group("Appointments", "⚑");
        appointments.add(MenuComponent.item("Register appointment", "/app/appointments/register",
                "+", Role::canRegisterAppointments));
        appointments.add(MenuComponent.item("Search appointments", "/app/appointments/search",
                "⚲", role -> true));
        appointments.add(MenuComponent.item("Day list", "/app/appointments/day",
                "☷", role -> true));
        appointments.add(MenuComponent.item("Reminder queue", "/app/reminders",
                "⏰", role -> true));
        root.add(appointments);

        MenuComponent records = MenuComponent.group("Clinic records", "☷");
        records.add(MenuComponent.item("Patients", "/app/patients", "⚇", role -> true));
        records.add(MenuComponent.item("Dentists", "/app/dentists", "⚕", role -> true));
        records.add(MenuComponent.item("Treatments", "/app/treatments", "⚒", role -> true));
        root.add(records);

        MenuComponent billing = MenuComponent.group("Billing", "¤");
        billing.add(MenuComponent.item("Issue a bill", "/app/billing", "¤",
                Role::canGenerateBills));
        root.add(billing);

        MenuComponent reports = MenuComponent.group("Reports", "◩");
        reports.add(MenuComponent.item("Daily appointments", "/app/reports?type=daily",
                "◩", Role::canViewOperationalReports));
        reports.add(MenuComponent.item("Dentist workload", "/app/reports?type=dentist",
                "◩", Role::canViewOperationalReports));
        reports.add(MenuComponent.item("Appointment status", "/app/reports?type=status",
                "◩", Role::canViewOperationalReports));
        reports.add(MenuComponent.item("Treatment revenue", "/app/reports?type=treatment",
                "◩", Role::canViewFinancialReports));
        reports.add(MenuComponent.item("Billing summary", "/app/reports?type=billing",
                "◩", Role::canViewFinancialReports));
        reports.add(MenuComponent.item("Clinic overview", "/app/reports?type=overview",
                "◩", Role::canViewFinancialReports));
        root.add(reports);

        MenuComponent administration = MenuComponent.group("Administration", "⚙");
        administration.add(MenuComponent.item("Staff accounts", "/app/users", "⚙",
                Role::canManageMasterData));
        root.add(administration);

        root.add(MenuComponent.item("Help", "/app/help", "?", role -> true));

        return root;
    }
}
