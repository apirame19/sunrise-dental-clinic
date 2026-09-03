package lk.sunrisedental.facade;

import lk.sunrisedental.billing.BillAssembler;
import lk.sunrisedental.dao.AppointmentDAO;
import lk.sunrisedental.dao.BillDAO;
import lk.sunrisedental.dao.DentistDAO;
import lk.sunrisedental.dao.PatientDAO;
import lk.sunrisedental.dao.ReportDAO;
import lk.sunrisedental.dao.TransactionManager;
import lk.sunrisedental.dao.TreatmentDAO;
import lk.sunrisedental.dao.UserDAO;
import lk.sunrisedental.dao.impl.JdbcAppointmentDAO;
import lk.sunrisedental.dao.impl.JdbcBillDAO;
import lk.sunrisedental.dao.impl.JdbcDentistDAO;
import lk.sunrisedental.dao.impl.JdbcPatientDAO;
import lk.sunrisedental.dao.impl.JdbcReportDAO;
import lk.sunrisedental.dao.impl.JdbcTransactionManager;
import lk.sunrisedental.dao.impl.JdbcTreatmentDAO;
import lk.sunrisedental.dao.impl.JdbcUserDAO;
import lk.sunrisedental.patterns.bridge.JdbcTreatmentPricingProvider;
import lk.sunrisedental.patterns.bridge.TreatmentPricingProvider;
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
import lk.sunrisedental.validation.ValidationChainBuilder;

import java.time.Clock;

/**
 * Builds the object graph the web tier runs on: DAOs, services and the facade.
 *
 * <p>All the wiring lives in one place so that a servlet never constructs a service and therefore
 * cannot construct one differently from its neighbour - for instance with a different
 * {@link Clock}, which would make "today" mean two things in one request.</p>
 *
 * <p>Uses the same initialisation-on-demand holder idiom as the two Singletons it depends on:
 * lazy, thread-safe, no locking on the read path. The services it builds are stateless and
 * therefore safe to share across concurrent requests; the only per-request state in the whole
 * business tier is the validation chain, which {@code ValidationChainBuilder} rebuilds each time
 * for exactly that reason.</p>
 *
 * <p>This is deliberately a plain factory rather than a dependency-injection container. The graph
 * is a dozen objects assembled once; a framework to do that would be more machinery than the
 * problem justifies, and the wiring would stop being readable in one screen.</p>
 */
public final class ServiceRegistry {

    private final ClinicManagementFacade facade;

    private static final class Holder {
        private static final ServiceRegistry INSTANCE = new ServiceRegistry(Clock.systemDefaultZone());

        private Holder() {
        }
    }

    /** @return the registry for this JVM. */
    public static ServiceRegistry getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Builds a registry against a caller-supplied clock.
     *
     * <p>Package-private so that production code goes through {@link #getInstance()}. A test that
     * needs a fixed clock builds its services directly rather than through here, because it also
     * needs mocked DAOs.</p>
     */
    ServiceRegistry(Clock clock) {
        ConfigurationManager configuration = ConfigurationManager.getInstance();

        UserDAO userDAO = new JdbcUserDAO();
        PatientDAO patientDAO = new JdbcPatientDAO();
        DentistDAO dentistDAO = new JdbcDentistDAO();
        TreatmentDAO treatmentDAO = new JdbcTreatmentDAO();
        AppointmentDAO appointmentDAO = new JdbcAppointmentDAO();
        BillDAO billDAO = new JdbcBillDAO();
        ReportDAO reportDAO = new JdbcReportDAO();
        TransactionManager transactionManager = new JdbcTransactionManager();

        ValidationChainBuilder validationChainBuilder =
                new ValidationChainBuilder(configuration, clock, dentistDAO, treatmentDAO);

        // BRIDGE: the live price source. Swapping this one line for the in-memory provider is all
        // that separates production billing from the billing the unit tests exercise.
        TreatmentPricingProvider pricingProvider =
                new JdbcTreatmentPricingProvider(treatmentDAO, configuration);

        this.facade = new ClinicManagementFacade(
                new AuthenticationService(userDAO, configuration, clock),
                new PatientService(patientDAO, appointmentDAO, billDAO, validationChainBuilder),
                new AppointmentService(appointmentDAO, patientDAO, dentistDAO, treatmentDAO,
                        validationChainBuilder, transactionManager),
                new BillingService(billDAO, appointmentDAO, pricingProvider, new BillAssembler(),
                        configuration, transactionManager, clock),
                new ReminderService(appointmentDAO, clock),
                new ReportService(reportDAO),
                new UserManagementService(userDAO),
                new DentistService(dentistDAO, appointmentDAO, configuration),
                new TreatmentService(treatmentDAO, configuration),
                new RegistrationService(userDAO, patientDAO, dentistDAO, transactionManager),
                configuration,
                clock);
    }

    /** @return the single entry point into the business tier. */
    public ClinicManagementFacade getFacade() {
        return facade;
    }
}
