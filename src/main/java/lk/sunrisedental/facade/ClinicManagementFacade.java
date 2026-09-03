package lk.sunrisedental.facade;

import lk.sunrisedental.dao.ReportRow;
import lk.sunrisedental.exception.AccessDeniedException;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AppointmentForm;
import lk.sunrisedental.model.AppointmentStatus;
import lk.sunrisedental.model.AvailabilitySlot;
import lk.sunrisedental.model.Bill;
import lk.sunrisedental.model.Dentist;
import lk.sunrisedental.model.Patient;
import lk.sunrisedental.model.PatientHistory;
import lk.sunrisedental.model.Reminder;
import lk.sunrisedental.model.Role;
import lk.sunrisedental.model.Treatment;
import lk.sunrisedental.model.User;
import lk.sunrisedental.patterns.composite.ReportSection;
import lk.sunrisedental.patterns.singleton.ConfigurationManager;
import lk.sunrisedental.service.AppointmentService;
import lk.sunrisedental.service.AuthenticationService;
import lk.sunrisedental.service.BillingService;
import lk.sunrisedental.service.DentistService;
import lk.sunrisedental.service.PatientService;
import lk.sunrisedental.service.RegistrationService;
import lk.sunrisedental.service.ReminderService;
import lk.sunrisedental.service.ReportService;
import lk.sunrisedental.service.TreatmentService;
import lk.sunrisedental.service.UserManagementService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * FACADE - the single entry point from the presentation tier into the business tier.
 *
 * <p><strong>Why this exists.</strong> Registering an appointment touches four services and a
 * validation chain; showing the dashboard touches three more. Without a facade every servlet would
 * wire those collaborators up itself, and the JSON API would wire them up again, slightly
 * differently. The two channels would then drift - which is how one of them ends up missing an
 * authorisation check. Controllers here depend on exactly one business type.</p>
 *
 * <p><strong>Authorisation lives here, and only here.</strong> Every method that needs a role check
 * performs it against {@link Role}'s own predicates before delegating. Putting these checks in the
 * servlets would mean writing each rule twice, once for HTML and once for JSON; putting them in the
 * services would entangle "what the clinic does" with "who may ask for it". The navigation menu
 * asks the same {@link Role} methods, so a menu item can never appear for a user who would be
 * refused on clicking it.</p>
 *
 * <p>This class contains no business rules of its own. It coordinates, authorises and assembles;
 * every decision about appointments, prices or bills belongs to the service it delegates to. That
 * restraint is what stops a facade turning into the god object the pattern is often accused of
 * producing.</p>
 */
public class ClinicManagementFacade {

    private final AuthenticationService authenticationService;
    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final BillingService billingService;
    private final ReminderService reminderService;
    private final ReportService reportService;
    private final UserManagementService userManagementService;
    private final DentistService dentistService;
    private final TreatmentService treatmentService;
    private final RegistrationService registrationService;
    private final ConfigurationManager configuration;
    private final Clock clock;

    public ClinicManagementFacade(AuthenticationService authenticationService,
                                  PatientService patientService,
                                  AppointmentService appointmentService,
                                  BillingService billingService,
                                  ReminderService reminderService,
                                  ReportService reportService,
                                  UserManagementService userManagementService,
                                  DentistService dentistService,
                                  TreatmentService treatmentService,
                                  RegistrationService registrationService,
                                  ConfigurationManager configuration,
                                  Clock clock) {
        this.authenticationService = authenticationService;
        this.patientService = patientService;
        this.appointmentService = appointmentService;
        this.billingService = billingService;
        this.reminderService = reminderService;
        this.reportService = reportService;
        this.userManagementService = userManagementService;
        this.dentistService = dentistService;
        this.treatmentService = treatmentService;
        this.registrationService = registrationService;
        this.configuration = configuration;
        this.clock = clock;
    }

    // ================================================================ authentication

    /**
     * Verifies staff credentials.
     *
     * @param username  the submitted username
     * @param password  the submitted password; wiped by the service
     * @param ipAddress the client address for the audit trail, or null
     * @return the authenticated user, to be placed in the session by the controller
     */
    public User signIn(String username, char[] password, String ipAddress) {
        return authenticationService.authenticate(username, password, ipAddress);
    }

    // ================================================================ self-registration

    /**
     * Registers a member of the public, creating their login and their patient record together.
     *
     * <p>There is deliberately no {@code actor} parameter and no {@code role} parameter. This is
     * the one family of operations in this facade that an anonymous visitor may invoke, and the
     * role it assigns is fixed in {@link RegistrationService} - {@link Role#ADMIN} is not reachable
     * from either registration path.</p>
     *
     * @param username      the sign-in name they chose
     * @param fullName      their name
     * @param address       their address
     * @param contactNumber their telephone number
     * @param email         their email address, optional
     * @param password      the password they chose; wiped by the service
     * @return the new account, active and able to sign in
     */
    public User registerPatientAccount(String username, String fullName, String address,
                                       String contactNumber, String email, char[] password) {
        return registrationService.registerPatient(
                username, fullName, address, contactNumber, email, password);
    }

    /**
     * Registers a dentist, creating their login and their roster entry together.
     *
     * @param username       the sign-in name they chose
     * @param fullName       their name
     * @param specialization their field of practice
     * @param licenseNo      their registration number
     * @param contactNumber  their telephone number
     * @param password       the password they chose; wiped by the service
     * @return the new account, <strong>inactive</strong> until an administrator approves it
     */
    public User registerDentistAccount(String username, String fullName, String specialization,
                                       String licenseNo, String contactNumber, char[] password) {
        return registrationService.registerDentist(
                username, fullName, specialization, licenseNo, contactNumber, password);
    }

    // ================================================================ patient portal

    /**
     * Assembles the signed-in patient's own record and visit history.
     *
     * <p><strong>Scoped by the session, never by a request parameter.</strong> The patient record
     * is resolved from the account in the session, so there is no id a patient could change to
     * read somebody else's history. This is the only history call that does not require staff
     * authority, and it is safe precisely because the caller cannot choose whose history it is.</p>
     *
     * @param actor the signed-in patient
     * @return their own history
     * @throws AccessDeniedException if the caller is not a patient
     * @throws BusinessRuleException if the account has no patient record bound to it
     */
    public PatientHistory myHistory(User actor) {
        if (actor == null || actor.getRole() != Role.PATIENT) {
            throw new AccessDeniedException("view your own appointments");
        }
        Patient patient = patientService.findByUserId(actor.getUserId())
                .orElseThrow(() -> new BusinessRuleException("NO_PATIENT_RECORD",
                        "No patient record is linked to this account. "
                        + "Please contact the clinic."));

        return patientService.getHistory(patient.getPatientId());
    }

    // ================================================================ dashboard

    /**
     * Assembles the dashboard in one call.
     *
     * @param actor the signed-in user
     * @param date  the day to show
     * @return the figures, the day's list and the reminder queue
     */
    public DashboardSnapshot dashboard(User actor, LocalDate date) {
        require(actor, Role::canViewOperationalReports, "view the dashboard");

        Optional<ReportRow> summary = reportService.dashboardSummary(date);
        boolean showRevenue = actor.getRole().canViewFinancialReports();

        return new DashboardSnapshot(
                date,
                summary.map(row -> row.getLong("today_total")).orElse(0L),
                summary.map(row -> row.getLong("today_scheduled")).orElse(0L),
                summary.map(row -> row.getLong("today_completed")).orElse(0L),
                summary.map(row -> row.getLong("today_cancelled")).orElse(0L),
                summary.map(row -> row.getLong("today_no_show")).orElse(0L),
                showRevenue
                        ? summary.map(row -> row.getMoney("today_revenue")).orElse(BigDecimal.ZERO)
                        : null,
                summary.map(row -> row.getLong("total_patients")).orElse(0L),
                summary.map(row -> row.getLong("active_dentists")).orElse(0L),
                summary.map(row -> row.getLong("upcoming_scheduled")).orElse(0L),
                appointmentService.findByDate(date),
                reminderService.buildQueue());
    }

    // ================================================================ patients

    /**
     * Registers a patient who is not being booked in at the same time.
     *
     * @param actor   the signed-in user
     * @param name    the patient's name
     * @param address where they live
     * @param contact their telephone number
     * @param email   optional email address
     * @return the stored patient
     */
    public Patient registerPatient(User actor, String name, String address, String contact,
                                   String email) {
        require(actor, Role::canRegisterAppointments, "register patients");
        return patientService.register(name, address, contact, email);
    }

    /**
     * @param actor the signed-in user
     * @param term  part of a name or contact number
     * @return matching patients; empty when the term is blank
     */
    public List<Patient> searchPatients(User actor, String term) {
        requireSignedIn(actor);
        return patientService.search(term);
    }

    /**
     * @param actor the signed-in user
     * @return every registered patient, ordered by name
     */
    public List<Patient> allPatients(User actor) {
        requireSignedIn(actor);
        return patientService.findAll();
    }

    /**
     * @param actor     the signed-in user
     * @param patientId the patient
     * @return the patient record
     */
    public Patient findPatient(User actor, int patientId) {
        requireSignedIn(actor);
        return patientService.findById(patientId);
    }

    /**
     * @param actor     the signed-in user
     * @param patientId the patient
     * @return every visit, bill and total for that patient
     */
    public PatientHistory patientHistory(User actor, int patientId) {
        requireSignedIn(actor);
        return patientService.getHistory(patientId);
    }

    /**
     * @param actor   the signed-in user
     * @param patient the patient carrying the new address, number and email
     * @return the updated record
     */
    public Patient updatePatientDetails(User actor, Patient patient) {
        require(actor, Role::canRegisterAppointments, "amend patient records");
        return patientService.updateContactDetails(patient);
    }

    // ================================================================ dentists

    /**
     * @param actor      the signed-in user
     * @param activeOnly {@code true} for the booking dropdown, {@code false} for the roster
     * @return the dentists
     */
    public List<Dentist> dentists(User actor, boolean activeOnly) {
        requireSignedIn(actor);
        return activeOnly ? dentistService.findAllActive() : dentistService.findAll();
    }

    /**
     * @param actor     the signed-in user
     * @param dentistId the dentist
     * @return the dentist
     */
    public Dentist findDentist(User actor, int dentistId) {
        requireSignedIn(actor);
        return dentistService.findById(dentistId);
    }

    /**
     * @param actor   an administrator
     * @param dentist the details to store
     * @return the stored dentist
     */
    public Dentist addDentist(User actor, Dentist dentist) {
        require(actor, Role::canManageMasterData, "add dentists");
        return dentistService.add(dentist);
    }

    /**
     * @param actor   an administrator
     * @param dentist the dentist carrying the new values and an existing id
     * @return the updated dentist
     */
    public Dentist updateDentist(User actor, Dentist dentist) {
        require(actor, Role::canManageMasterData, "amend dentist records");
        return dentistService.update(dentist);
    }

    /**
     * @param actor     an administrator
     * @param dentistId the dentist
     * @param active    {@code true} to reinstate, {@code false} to withdraw from the booking list
     * @return the updated dentist
     */
    public Dentist setDentistActive(User actor, int dentistId, boolean active) {
        require(actor, Role::canManageMasterData, "withdraw or reinstate dentists");
        return dentistService.setActive(dentistId, active, LocalDate.now(clock));
    }

    /**
     * @param actor     the signed-in user
     * @param dentistId the dentist
     * @param date      the day
     * @return that dentist's appointments for the day
     */
    public List<Appointment> dentistSchedule(User actor, int dentistId, LocalDate date) {
        requireSignedIn(actor);
        return dentistService.schedule(dentistId, date);
    }

    /**
     * @param actor     the signed-in user
     * @param dentistId the dentist
     * @param date      the day
     * @return every slot in the opening hours, marked free or taken
     */
    public List<AvailabilitySlot> dentistAvailability(User actor, int dentistId, LocalDate date) {
        requireSignedIn(actor);
        return dentistService.availability(dentistId, date);
    }

    // ================================================================ treatments

    /**
     * @param actor      the signed-in user
     * @param activeOnly {@code true} for the booking dropdown, {@code false} for the full catalogue
     * @return the treatments
     */
    public List<Treatment> treatments(User actor, boolean activeOnly) {
        requireSignedIn(actor);
        return activeOnly ? treatmentService.findAllActive() : treatmentService.findAll();
    }

    /**
     * @param actor       the signed-in user
     * @param treatmentId the treatment
     * @return the treatment
     */
    public Treatment findTreatment(User actor, int treatmentId) {
        requireSignedIn(actor);
        return treatmentService.findById(treatmentId);
    }

    /**
     * @param treatment the treatment to price
     * @return list price plus consultation fee and any levy - an indication, never a bill
     */
    public BigDecimal indicativeTotal(Treatment treatment) {
        return treatmentService.indicativeTotal(treatment);
    }

    // ================================================================ appointments

    /**
     * @param date the appointment date
     * @return the next unused appointment number, to prefill the booking form
     */
    public String suggestAppointmentNo(LocalDate date) {
        return appointmentService.suggestAppointmentNo(date);
    }

    /**
     * Registers an appointment, creating the patient record if this is someone new.
     *
     * @param actor the signed-in user
     * @param form  the submitted details
     * @return the stored appointment
     */
    public Appointment registerAppointment(User actor, AppointmentForm form) {
        require(actor, Role::canRegisterAppointments, "register appointments");
        return appointmentService.register(form, actor);
    }

    /**
     * @param actor         the signed-in user
     * @param appointmentNo the number to search for
     * @return the appointment if it exists; absence is an ordinary outcome, not an error
     */
    public Optional<Appointment> searchAppointment(User actor, String appointmentNo) {
        requireSignedIn(actor);
        return appointmentService.search(appointmentNo);
    }

    /**
     * @param actor         the signed-in user
     * @param appointmentNo the number to look up
     * @return the appointment
     */
    public Appointment findAppointment(User actor, String appointmentNo) {
        requireSignedIn(actor);
        return appointmentService.findByNumber(appointmentNo);
    }

    /**
     * @param actor the signed-in user
     * @param date  the day
     * @return every appointment that day
     */
    public List<Appointment> appointmentsOn(User actor, LocalDate date) {
        requireSignedIn(actor);
        return appointmentService.findByDate(date);
    }

    /**
     * @param actor the signed-in user
     * @param from  inclusive start
     * @param to    inclusive end
     * @return appointments in the range
     */
    public List<Appointment> appointmentsBetween(User actor, LocalDate from, LocalDate to) {
        requireSignedIn(actor);
        return appointmentService.findByDateRange(from, to);
    }

    /**
     * Records the outcome of a visit, or cancels it.
     *
     * @param actor         the signed-in user
     * @param appointmentNo the appointment
     * @param newStatus     the status to move to
     * @param reason        why, required when cancelling
     * @return the updated appointment
     */
    public Appointment updateAppointmentStatus(User actor, String appointmentNo,
                                               AppointmentStatus newStatus, String reason) {
        require(actor, Role::canUpdateAppointmentStatus,
                "change appointment status");
        return appointmentService.updateStatus(appointmentNo, newStatus, reason);
    }

    // ================================================================ billing

    /**
     * Works out what a visit would cost, without storing anything.
     *
     * @param actor         the signed-in user
     * @param appointmentNo the appointment to price
     * @return an unsaved bill carrying the itemised breakdown
     */
    public Bill previewBill(User actor, String appointmentNo) {
        require(actor, Role::canGenerateBills, "view billing");
        return billingService.preview(appointmentNo);
    }

    /**
     * Issues and stores a bill for a completed visit.
     *
     * @param actor         the member of staff issuing it
     * @param appointmentNo the appointment to bill
     * @return the stored bill
     */
    public Bill generateBill(User actor, String appointmentNo) {
        require(actor, Role::canGenerateBills, "issue bills");
        return billingService.generate(appointmentNo, actor);
    }

    /**
     * @param actor         the signed-in user
     * @param appointmentNo the appointment
     * @return the bill already issued for it, if any
     */
    public Optional<Bill> findBillForAppointment(User actor, String appointmentNo) {
        requireSignedIn(actor);
        return billingService.findForAppointment(appointmentNo);
    }

    /**
     * @param actor  the signed-in user
     * @param billNo the bill number
     * @return the bill including its itemised lines
     */
    public Optional<Bill> findBill(User actor, String billNo) {
        requireSignedIn(actor);
        return billingService.findByBillNo(billNo);
    }

    /**
     * @param actor an administrator
     * @param from  inclusive start
     * @param to    inclusive end
     * @return bills issued in the range
     */
    public List<Bill> billsBetween(User actor, LocalDate from, LocalDate to) {
        require(actor, Role::canViewFinancialReports, "list issued bills");
        return billingService.findByDateRange(from, to);
    }

    // ================================================================ reminders

    /**
     * @param actor         the signed-in user
     * @param lookAheadDays how many days ahead to include
     * @return the reminder queue, overdue outcomes first
     */
    public List<Reminder> reminderQueue(User actor, int lookAheadDays) {
        requireSignedIn(actor);
        return reminderService.buildQueue(lookAheadDays);
    }

    // ================================================================ reports

    /**
     * Runs one of the decision-support reports.
     *
     * <p>The revenue-bearing reports are administrator-only. That check is made here, once, so the
     * HTML report screen and the JSON reports endpoint cannot disagree about who may see takings.</p>
     *
     * @param actor     the signed-in user
     * @param type      one of {@code daily}, {@code dentist}, {@code treatment}, {@code billing},
     *                  {@code status} or {@code overview}
     * @param from      inclusive start
     * @param to        inclusive end
     * @param dentistId a single dentist for the dentist report, or {@code null} for all
     * @return the report tree
     * @throws BusinessRuleException if the report type is not recognised
     */
    public ReportSection report(User actor, String type, LocalDate from, LocalDate to,
                                Integer dentistId) {
        requireSignedIn(actor);
        String requested = type == null ? "daily" : type.trim().toLowerCase();

        return switch (requested) {
            case "daily" -> {
                require(actor, Role::canViewOperationalReports,
                        "view the daily appointment report");
                yield reportService.dailyAppointmentReport(from);
            }
            case "dentist" -> {
                require(actor, Role::canViewOperationalReports,
                        "view the dentist appointment report");
                yield reportService.dentistAppointmentReport(from, to, dentistId);
            }
            case "status" -> {
                require(actor, Role::canViewOperationalReports,
                        "view the appointment status summary");
                yield reportService.appointmentStatusSummary(from, to);
            }
            case "treatment" -> {
                require(actor, Role::canViewFinancialReports,
                        "view the treatment and revenue report");
                yield reportService.treatmentRevenueReport(from, to);
            }
            case "billing" -> {
                require(actor, Role::canViewFinancialReports,
                        "view the billing summary");
                yield reportService.billingSummaryReport(from, to);
            }
            case "overview" -> {
                require(actor, Role::canViewFinancialReports,
                        "view the clinic overview");
                yield reportService.clinicOverview(from);
            }
            default -> throw new BusinessRuleException("UNKNOWN_REPORT",
                    "There is no report of type '" + type + "'.");
        };
    }

    /**
     * @param actor     the signed-in user
     * @param patientId the patient
     * @return that patient's visit history as a report tree, grouped by year
     */
    public ReportSection patientHistoryReport(User actor, int patientId) {
        requireSignedIn(actor);
        Patient patient = patientService.findById(patientId);
        return reportService.patientHistoryReport(patientId, patient.getPatientName());
    }

    // ================================================================ staff accounts

    /**
     * @param actor an administrator
     * @return every staff account
     */
    public List<User> staff(User actor) {
        require(actor, Role::canManageMasterData, "manage staff accounts");
        return userManagementService.findAll();
    }

    /**
     * @param actor  an administrator
     * @param userId the account
     * @return the account
     */
    public User findStaff(User actor, int userId) {
        require(actor, Role::canManageMasterData, "manage staff accounts");
        return userManagementService.findById(userId);
    }

    /**
     * @param actor    an administrator
     * @param username the sign-in name
     * @param fullName the person's name
     * @param role     what the account may do
     * @param password the initial password; wiped by the service
     * @return the stored account
     */
    public User addStaff(User actor, String username, String fullName, Role role, char[] password) {
        require(actor, Role::canManageMasterData, "create staff accounts");
        return userManagementService.create(username, fullName, role, password);
    }

    /**
     * @param actor    an administrator
     * @param userId   the account to change
     * @param fullName the new name
     * @param role     the new role
     * @return the updated account
     */
    public User updateStaff(User actor, int userId, String fullName, Role role) {
        require(actor, Role::canManageMasterData, "amend staff accounts");
        return userManagementService.update(userId, fullName, role, actor);
    }

    /**
     * @param actor  an administrator
     * @param userId the account
     * @param active {@code true} to allow sign-in again, {@code false} to refuse it
     * @return the updated account
     */
    public User setStaffActive(User actor, int userId, boolean active) {
        require(actor, Role::canManageMasterData,
                "deactivate or reactivate staff accounts");
        return userManagementService.setActive(userId, active, actor);
    }

    /**
     * @param actor       an administrator
     * @param userId      the account
     * @param newPassword the new password; wiped by the service
     * @return the account
     */
    public User resetStaffPassword(User actor, int userId, char[] newPassword) {
        require(actor, Role::canManageMasterData, "reset staff passwords");
        return userManagementService.resetPassword(userId, newPassword);
    }

    // ================================================================ configuration

    /** @return the cached clinic settings, for display on the help screen and in views. */
    public ConfigurationManager configuration() {
        return configuration;
    }

    /** @return today's date according to the injected clock. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ================================================================ authorisation

    /**
     * @param actor     the signed-in user
     * @param permitted the question to ask of that user's role
     * @param action    what they were trying to do, for the message
     * @throws AccessDeniedException if the role does not permit it, or nobody is signed in
     */
    private static void require(User actor, Predicate<Role> permitted, String action) {
        requireSignedIn(actor);
        if (!permitted.test(actor.getRole())) {
            throw new AccessDeniedException(action);
        }
    }

    /**
     * Guards against a null user reaching the business tier.
     *
     * <p>The authentication filter should make this impossible. It is checked anyway, because
     * "unreachable" security checks are exactly the ones that become reachable when a new servlet
     * is added outside the filter's URL pattern.</p>
     */
    private static void requireSignedIn(User actor) {
        if (actor == null) {
            throw new AccessDeniedException("use this feature while signed out");
        }
    }
}
