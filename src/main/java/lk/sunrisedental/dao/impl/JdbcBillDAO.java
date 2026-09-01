package lk.sunrisedental.dao.impl;

import lk.sunrisedental.dao.BillDAO;
import lk.sunrisedental.dao.SqlErrorTranslator;
import lk.sunrisedental.model.Bill;
import lk.sunrisedental.model.BillLine;
import lk.sunrisedental.model.BillLineType;
import lk.sunrisedental.patterns.singleton.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO - plain JDBC data access for bills and their itemised lines.
 *
 * <p>Every method is written out in full so the database work is visible: borrow a
 * {@link Connection} from the {@link DatabaseConnectionManager} singleton, prepare a
 * {@link PreparedStatement}, then read a {@link ResultSet}, all inside try-with-resources.</p>
 *
 * <p>A bill and its itemised lines are always written together on the caller's transactional
 * connection. They form a composition, and a committed bill whose lines failed to write would be
 * unprintable - the receipt would show a total with nothing explaining it.</p>
 *
 * <p><strong>Not every read loads the lines.</strong> {@link #findById}, {@link #findByBillNo} and
 * {@link #findByAppointmentId(int)} do, because they serve the receipt. The transactional
 * {@link #findByAppointmentId(Connection, int)} and {@link #findByDateRange} do not: the first only
 * needs to know whether a bill exists, and the second feeds a revenue list of hundreds of rows
 * where loading every line would be a query per bill for information nothing displays.</p>
 *
 * <p><strong>Two methods take a connection instead of borrowing one</strong> -
 * {@link #insert(Bill, int, Connection)} and {@link #findByAppointmentId(Connection, int)}. They
 * close only the statement and result set; the connection belongs to the caller's transaction.</p>
 */
public class JdbcBillDAO implements BillDAO {

    private static final String COLUMNS =
            "bill_id, bill_no, appointment_id, treatment_cost, consultation_fee, discount_amount, "
            + "tax_amount, total_amount, generated_by, generated_at";

    private static final String SELECT_BY_ID =
            "SELECT " + COLUMNS + " FROM bills WHERE bill_id = ?";

    private static final String SELECT_BY_NO =
            "SELECT " + COLUMNS + " FROM bills WHERE bill_no = ?";

    private static final String SELECT_BY_APPOINTMENT =
            "SELECT " + COLUMNS + " FROM bills WHERE appointment_id = ?";

    private static final String SELECT_BY_DATE_RANGE =
            "SELECT " + COLUMNS + " FROM bills "
            + "WHERE DATE(generated_at) BETWEEN ? AND ? ORDER BY generated_at DESC";

    private static final String SELECT_LINES =
            "SELECT line_no, line_type, description, amount FROM bill_items "
            + "WHERE bill_id = ? ORDER BY line_no";

    private static final String INSERT_BILL =
            "INSERT INTO bills (bill_no, appointment_id, treatment_cost, consultation_fee, "
            + "discount_amount, tax_amount, total_amount, generated_by) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_LINE =
            "INSERT INTO bill_items (bill_id, line_no, line_type, description, amount) "
            + "VALUES (?, ?, ?, ?, ?)";

    private static final String NEXT_BILL_NO = "SELECT fn_next_bill_no(?)";

    private static final String EXISTS_FOR_APPOINTMENT =
            "SELECT COUNT(*) FROM bills WHERE appointment_id = ?";

    /** SINGLETON - the application's one source of MySQL connections. */
    private final DatabaseConnectionManager connectionManager;

    public JdbcBillDAO() {
        this(DatabaseConnectionManager.getInstance());
    }

    /**
     * @param connectionManager the source of connections; injectable so a test can point this DAO
     *                          at a test schema
     */
    public JdbcBillDAO(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // ------------------------------------------------------------------ reads with lines

    @Override
    public Optional<Bill> findById(int billId) {
        Optional<Bill> bill;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {

            statement.setInt(1, billId);

            try (ResultSet resultSet = statement.executeQuery()) {
                bill = resultSet.next() ? Optional.of(mapBill(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading bill " + billId);
        }

        return bill.map(this::withLines);
    }

    @Override
    public Optional<Bill> findByBillNo(String billNo) {
        Optional<Bill> bill;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_NO)) {

            statement.setString(1, billNo);

            try (ResultSet resultSet = statement.executeQuery()) {
                bill = resultSet.next() ? Optional.of(mapBill(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading bill " + billNo);
        }

        return bill.map(this::withLines);
    }

    @Override
    public Optional<Bill> findByAppointmentId(int appointmentId) {
        Optional<Bill> bill;

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_APPOINTMENT)) {

            statement.setInt(1, appointmentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                bill = resultSet.next() ? Optional.of(mapBill(resultSet)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Loading the bill for appointment " + appointmentId);
        }

        return bill.map(this::withLines);
    }

    /**
     * As above, but on the caller's transactional connection.
     *
     * <p>The connection is NOT closed here - it belongs to the caller and must stay open for the
     * rest of the transaction. Only the statement and result set are closed.</p>
     *
     * <p>The itemised lines are deliberately not loaded. This is the "has this visit already been
     * billed?" check made inside the billing transaction, and nothing there reads the lines.</p>
     */
    @Override
    public Optional<Bill> findByAppointmentId(Connection connection, int appointmentId) {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_APPOINTMENT)) {

            statement.setInt(1, appointmentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapBill(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Loading the bill for appointment " + appointmentId);
        }
    }

    /** Attaches the itemised lines to a bill that has already been read. */
    private Bill withLines(Bill bill) {
        bill.setLines(loadLines(bill.getBillId(), bill.getBillNo()));
        return bill;
    }

    /** Reads the {@code bill_items} rows for one bill, in line order. */
    private List<BillLine> loadLines(int billId, String billNo) {
        List<BillLine> lines = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_LINES)) {

            statement.setInt(1, billId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lines.add(mapBillLine(resultSet));
                }
            }
            return lines;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading lines for bill " + billNo);
        }
    }

    // ------------------------------------------------------------------ reads without lines

    @Override
    public List<Bill> findByDateRange(LocalDate from, LocalDate to) {
        List<Bill> bills = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_DATE_RANGE)) {

            statement.setObject(1, from);
            statement.setObject(2, to);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    bills.add(mapBill(resultSet));
                }
            }
            return bills;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Listing bills between " + from + " and " + to);
        }
    }

    @Override
    public boolean existsForAppointment(int appointmentId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(EXISTS_FOR_APPOINTMENT)) {

            statement.setInt(1, appointmentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Checking whether appointment " + appointmentId + " is already billed");
        }
    }

    @Override
    public String generateNextBillNo(LocalDate date) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(NEXT_BILL_NO)) {

            statement.setObject(1, date);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String billNo = resultSet.getString(1);
                    if (billNo != null) {
                        return billNo;
                    }
                }
                throw new IllegalStateException("fn_next_bill_no returned no value");
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Generating the next bill number");
        }
    }

    // ------------------------------------------------------------------ writes

    /**
     * Writes a bill and all of its lines on the caller's transactional connection.
     *
     * <p>The connection is NOT closed here - the caller owns it, and the bill and its lines must
     * commit or roll back as one unit. Only the statement and the generated-keys result set are
     * closed.</p>
     *
     * @return the generated bill id
     */
    @Override
    public int insert(Bill bill, int generatedBy, Connection connection) {
        int billId;

        try (PreparedStatement statement =
                     connection.prepareStatement(INSERT_BILL, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, bill.getBillNo());
            statement.setInt(2, bill.getAppointmentId());
            statement.setBigDecimal(3, bill.getTreatmentCost());
            statement.setBigDecimal(4, bill.getConsultationFee());
            statement.setBigDecimal(5, bill.getDiscountAmount());
            statement.setBigDecimal(6, bill.getTaxAmount());
            statement.setBigDecimal(7, bill.getTotalAmount());
            statement.setInt(8, generatedBy);

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    billId = keys.getInt(1);
                } else {
                    // The bill went in but we cannot tell the caller its id, and without it the
                    // lines below could not be attached to anything.
                    throw new SQLException("Issuing a bill returned no generated key");
                }
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Issuing bill " + bill.getBillNo());
        }

        insertLines(connection, billId, bill.getLines());
        return billId;
    }

    /**
     * Writes the itemised lines as a single batch, on the caller's connection.
     *
     * <p>Batched because a bill has three or four lines and a round trip each would triple the
     * time the transaction holds its locks for no benefit. The connection is not closed here.</p>
     */
    private void insertLines(Connection connection, int billId, List<BillLine> lines) {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_LINE)) {

            for (BillLine line : lines) {
                statement.setInt(1, billId);
                statement.setInt(2, line.getLineNo());
                statement.setString(3, line.getLineType().name());
                statement.setString(4, line.getDescription());
                statement.setBigDecimal(5, line.getAmount());
                statement.addBatch();
            }
            statement.executeBatch();

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Writing bill lines for bill " + billId);
        }
    }

    // ------------------------------------------------------------------ row mapping

    /**
     * Copies the current row of {@code bills} into a {@link Bill}. Itemised lines are loaded
     * separately by {@link #loadLines}.
     *
     * <p>Money columns are read with {@code getBigDecimal} so that no amount ever passes through
     * a binary floating-point type on its way out of the database.</p>
     */
    private static Bill mapBill(ResultSet resultSet) throws SQLException {
        Bill bill = new Bill();
        bill.setBillId(resultSet.getInt("bill_id"));
        bill.setBillNo(resultSet.getString("bill_no"));
        bill.setAppointmentId(resultSet.getInt("appointment_id"));
        bill.setTreatmentCost(resultSet.getBigDecimal("treatment_cost"));
        bill.setConsultationFee(resultSet.getBigDecimal("consultation_fee"));
        bill.setDiscountAmount(resultSet.getBigDecimal("discount_amount"));
        bill.setTaxAmount(resultSet.getBigDecimal("tax_amount"));
        bill.setTotalAmount(resultSet.getBigDecimal("total_amount"));
        bill.setGeneratedById(resultSet.getInt("generated_by"));
        bill.setGeneratedAt(resultSet.getObject("generated_at", LocalDateTime.class));
        return bill;
    }

    /** Copies the current row of {@code bill_items} into a {@link BillLine}. */
    private static BillLine mapBillLine(ResultSet resultSet) throws SQLException {
        return new BillLine(
                resultSet.getInt("line_no"),
                BillLineType.fromString(resultSet.getString("line_type"))
                        .orElse(BillLineType.TREATMENT),
                resultSet.getString("description"),
                resultSet.getBigDecimal("amount"));
    }
}
