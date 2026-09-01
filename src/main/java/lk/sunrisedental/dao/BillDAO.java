package lk.sunrisedental.dao;

import lk.sunrisedental.model.Bill;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Data access for bills and their itemised lines.
 *
 * <p>A bill and its lines are written together in one transaction through
 * {@link #insert(Bill, int, Connection)}. They are a composition: lines without their bill are
 * meaningless, and a bill without its lines cannot be reprinted as a receipt. Committing one
 * without the other would produce exactly the billing inconsistency this system exists to
 * eliminate.</p>
 *
 * <p>There is no update method for amounts. An issued bill is a financial record; a correction is
 * a new bill. The database enforces the same thing through {@code trg_bills_before_update}.</p>
 */
public interface BillDAO {

    /**
     * @param billId the primary key
     * @return the bill including its itemised lines, or empty if not found
     */
    Optional<Bill> findById(int billId);

    /**
     * @param billNo the business key, for example {@code BILL-20260815-001}
     * @return the bill including its itemised lines, or empty if not found
     */
    Optional<Bill> findByBillNo(String billNo);

    /**
     * @param appointmentId the appointment
     * @return the bill for that visit, or empty if it has not been billed
     */
    Optional<Bill> findByAppointmentId(int appointmentId);

    /** As {@link #findByAppointmentId}, on a caller-supplied transactional connection. */
    Optional<Bill> findByAppointmentId(Connection connection, int appointmentId);

    /**
     * Writes a bill and all of its lines inside one transaction.
     *
     * @param bill        the bill, with its lines already populated by the billing decorators
     * @param generatedBy the user issuing it
     * @param connection  the transactional connection
     * @return the generated bill id
     */
    int insert(Bill bill, int generatedBy, Connection connection);

    /**
     * @param date the bill date
     * @return the next unused bill number of the form {@code BILL-YYYYMMDD-NNN}
     */
    String generateNextBillNo(LocalDate date);

    /**
     * @param from inclusive start date
     * @param to   inclusive end date
     * @return bills issued in the range, most recent first; lines are not loaded
     */
    List<Bill> findByDateRange(LocalDate from, LocalDate to);

    /**
     * @param appointmentId the appointment
     * @return {@code true} if this visit has already been billed
     */
    boolean existsForAppointment(int appointmentId);
}
