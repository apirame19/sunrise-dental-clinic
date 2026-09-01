package lk.sunrisedental.service;

import lk.sunrisedental.billing.BillAssembler;
import lk.sunrisedental.dao.AppointmentDAO;
import lk.sunrisedental.dao.BillDAO;
import lk.sunrisedental.dao.TransactionManager;
import lk.sunrisedental.exception.AppointmentNotFoundException;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.model.Appointment;
import lk.sunrisedental.model.AppointmentStatus;
import lk.sunrisedental.model.Bill;
import lk.sunrisedental.model.User;
import lk.sunrisedental.patterns.bridge.BillCalculator;
import lk.sunrisedental.patterns.bridge.BillingContext;
import lk.sunrisedental.patterns.bridge.BillingPlan;
import lk.sunrisedental.patterns.bridge.FollowUpBillCalculator;
import lk.sunrisedental.patterns.bridge.StandardBillCalculator;
import lk.sunrisedental.patterns.bridge.TreatmentPricingProvider;
import lk.sunrisedental.patterns.singleton.ConfigurationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Produces and stores bills.
 *
 * <p>This service chooses the billing policy and then delegates the arithmetic. It decides
 * <em>which</em> Bridge refinement applies by looking at the patient's history, hands the decision
 * to that calculator, and passes the resulting plan to the Decorator chain for itemisation. The
 * service itself contains no monetary arithmetic at all, which is why the billing rules could be
 * developed and tested with no database.</p>
 *
 * <p>Three rules are enforced before anything is written, each mirrored by a database guard so
 * that a friendly message wins and the trigger remains the last line of defence:</p>
 *
 * <ul>
 *   <li>Only a completed visit may be billed - a booking is not income ({@code SDC-201}).</li>
 *   <li>A visit is billed at most once ({@code uk_bills_appointment}).</li>
 *   <li>The stored total must equal the sum of its components ({@code SDC-202}).</li>
 * </ul>
 */
public class BillingService {

    private final BillDAO billDAO;
    private final AppointmentDAO appointmentDAO;
    private final TreatmentPricingProvider pricingProvider;
    private final BillAssembler billAssembler;
    private final ConfigurationManager configuration;
    private final TransactionManager transactionManager;
    private final Clock clock;

    public BillingService(BillDAO billDAO,
                          AppointmentDAO appointmentDAO,
                          TreatmentPricingProvider pricingProvider,
                          BillAssembler billAssembler,
                          ConfigurationManager configuration,
                          TransactionManager transactionManager,
                          Clock clock) {
        this.billDAO = billDAO;
        this.appointmentDAO = appointmentDAO;
        this.pricingProvider = pricingProvider;
        this.billAssembler = billAssembler;
        this.configuration = configuration;
        this.transactionManager = transactionManager;
        this.clock = clock;
    }

    /**
     * Works out what a visit would cost, without storing anything.
     *
     * <p>Used to answer "how much will this cost?" at the desk before treatment, and to preview a
     * bill before issuing it.</p>
     *
     * @param appointmentNo the appointment to price
     * @return an unsaved bill carrying the itemised breakdown
     */
    public Bill preview(String appointmentNo) {
        Appointment appointment = requireAppointment(appointmentNo);
        BillingPlan plan = planFor(appointment);
        Bill bill = billAssembler.assemble(plan);
        bill.setAppointmentId(appointment.getAppointmentId());
        bill.setAppointmentNo(appointment.getAppointmentNo());
        bill.setAppointment(appointment);
        return bill;
    }

    /**
     * Issues and stores a bill for a completed visit.
     *
     * @param appointmentNo the appointment to bill
     * @param issuedBy      the member of staff issuing it
     * @return the stored bill, with its number and itemised lines
     * @throws BusinessRuleException if the visit is not completed, or is already billed
     */
    public Bill generate(String appointmentNo, User issuedBy) {
        Appointment appointment = requireAppointment(appointmentNo);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BusinessRuleException("CANCELLED_NOT_BILLABLE",
                    "Appointment " + appointment.getAppointmentNo()
                    + " was cancelled and cannot be billed.");
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException("NOT_COMPLETED",
                    "Appointment " + appointment.getAppointmentNo() + " is "
                    + appointment.getStatus().getLabel().toLowerCase()
                    + ". Only a completed visit can be billed.");
        }

        Optional<Bill> existing = billDAO.findByAppointmentId(appointment.getAppointmentId());
        if (existing.isPresent()) {
            throw new BusinessRuleException("BILL_ALREADY_EXISTS",
                    "A bill has already been issued for this appointment ("
                    + existing.get().getBillNo() + ").");
        }

        BillingPlan plan = planFor(appointment);
        Bill bill = billAssembler.assemble(plan);
        bill.setAppointmentId(appointment.getAppointmentId());
        bill.setAppointmentNo(appointment.getAppointmentNo());
        bill.setBillNo(billDAO.generateNextBillNo(LocalDate.now(clock)));

        int billId = transactionManager.execute(connection -> {
            // Re-checked inside the transaction: two receptionists could both reach here.
            if (billDAO.findByAppointmentId(connection, appointment.getAppointmentId()).isPresent()) {
                throw new BusinessRuleException("BILL_ALREADY_EXISTS",
                        "A bill was issued for this appointment a moment ago.");
            }
            return billDAO.insert(bill, issuedBy.getUserId(), connection);
        });

        return billDAO.findById(billId)
                .map(stored -> {
                    stored.setAppointment(appointment);
                    stored.setAppointmentNo(appointment.getAppointmentNo());
                    stored.setGeneratedByName(issuedBy.getFullName());
                    return stored;
                })
                .orElseThrow(() -> new BusinessRuleException("BILL_SAVE_FAILED",
                        "The bill was saved but could not be read back."));
    }

    /**
     * @param appointmentNo the appointment
     * @return the bill already issued for it, if any
     */
    public Optional<Bill> findForAppointment(String appointmentNo) {
        return appointmentDAO.findByAppointmentNo(appointmentNo)
                .flatMap(appointment -> billDAO.findByAppointmentId(appointment.getAppointmentId())
                        .map(bill -> {
                            bill.setAppointment(appointment);
                            bill.setAppointmentNo(appointment.getAppointmentNo());
                            return bill;
                        }));
    }

    /**
     * @param billNo the bill number
     * @return the bill including its lines, if it exists
     */
    public Optional<Bill> findByBillNo(String billNo) {
        return billDAO.findByBillNo(billNo);
    }

    /**
     * @param from inclusive start
     * @param to   inclusive end
     * @return bills issued in the range
     */
    public List<Bill> findByDateRange(LocalDate from, LocalDate to) {
        return billDAO.findByDateRange(from, to);
    }

    /**
     * Chooses the billing policy - the Bridge's abstraction axis - and produces the figures.
     *
     * <p>A visit counts as a follow-up when the same patient had the same treatment completed
     * within the configured window. That is a genuine clinical judgement expressed in data, not a
     * flag someone ticks, so it is derived here from the appointment history.</p>
     */
    private BillingPlan planFor(Appointment appointment) {
        boolean followUp = isFollowUp(appointment);

        BillCalculator calculator = followUp
                ? new FollowUpBillCalculator(pricingProvider)
                : new StandardBillCalculator(pricingProvider);

        return calculator.plan(new BillingContext(
                appointment.getTreatment().getTreatmentId(), followUp));
    }

    /**
     * @return {@code true} if this patient had the same treatment completed within the follow-up
     *         window, which is what earns the reduced terms
     */
    private boolean isFollowUp(Appointment appointment) {
        int windowDays = configuration.getFollowUpWindowDays();
        LocalDate earliest = appointment.getAppointmentDate().minusDays(windowDays);

        return appointmentDAO.findByPatientId(appointment.getPatient().getPatientId()).stream()
                .filter(previous -> previous.getAppointmentId() != appointment.getAppointmentId())
                .filter(previous -> previous.getStatus() == AppointmentStatus.COMPLETED)
                .filter(previous -> previous.getTreatment().getTreatmentId()
                        == appointment.getTreatment().getTreatmentId())
                .anyMatch(previous -> !previous.getAppointmentDate().isBefore(earliest)
                        && previous.getAppointmentDate().isBefore(appointment.getAppointmentDate()));
    }

    private Appointment requireAppointment(String appointmentNo) {
        String trimmed = appointmentNo == null ? "" : appointmentNo.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessRuleException("EMPTY_SEARCH",
                    "Please enter an appointment number.");
        }
        return appointmentDAO.findByAppointmentNo(trimmed)
                .orElseThrow(() -> new AppointmentNotFoundException(trimmed));
    }
}
