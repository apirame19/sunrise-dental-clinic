package lk.sunrisedental.dao.impl;

import lk.sunrisedental.dao.SqlErrorTranslator;
import lk.sunrisedental.dao.TreatmentDAO;
import lk.sunrisedental.model.Treatment;
import lk.sunrisedental.patterns.singleton.DatabaseConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO - plain JDBC data access for the treatment price list.
 *
 * <p>Every method follows the same visible three steps: borrow a {@link Connection} from the
 * {@link DatabaseConnectionManager} singleton, prepare a {@link PreparedStatement}, then read a
 * {@link ResultSet} - all inside try-with-resources, so each resource is closed on every path
 * including exceptions. Values are bound with {@code statement.setX(...)}, never concatenated into
 * the SQL text.</p>
 *
 * <p>This interface is read-only. Treatments and their prices are clinic master data, maintained
 * directly in the database rather than through the application, so there is no insert or update
 * here and no generated-key handling.</p>
 *
 * <p><strong>{@code base_cost} is read as a {@link java.math.BigDecimal}</strong> and never as a
 * {@code double}. Every bill is built from this figure by the billing decorators, and binary
 * floating point cannot represent money exactly - a rounding error introduced here would be
 * multiplied through the consultation fee, the discount and the tax on every receipt the clinic
 * issues.</p>
 *
 * <p><strong>Active and inactive are both readable, deliberately.</strong> {@link #findAllActive}
 * feeds the booking dropdown, so a withdrawn treatment cannot be chosen for a new appointment.
 * {@link #findById} and {@link #findByCode} ignore the flag, because an appointment booked last
 * week must still be billable even if its treatment was withdrawn from the menu yesterday.</p>
 */
public class JdbcTreatmentDAO implements TreatmentDAO {

    private static final String COLUMNS =
            "treatment_id, treatment_code, treatment_name, description, base_cost, "
            + "duration_minutes, is_taxable, is_active";

    private static final String SELECT_BY_ID =
            "SELECT " + COLUMNS + " FROM treatments WHERE treatment_id = ?";

    private static final String SELECT_BY_CODE =
            "SELECT " + COLUMNS + " FROM treatments WHERE treatment_code = ?";

    private static final String SELECT_ACTIVE =
            "SELECT " + COLUMNS + " FROM treatments WHERE is_active = 1 ORDER BY treatment_name";

    private static final String SELECT_ALL =
            "SELECT " + COLUMNS + " FROM treatments ORDER BY is_active DESC, treatment_name";

    private static final String EXISTS_ACTIVE =
            "SELECT COUNT(*) FROM treatments WHERE treatment_id = ? AND is_active = 1";

    /** SINGLETON - the application's one source of MySQL connections. */
    private final DatabaseConnectionManager connectionManager;

    public JdbcTreatmentDAO() {
        this(DatabaseConnectionManager.getInstance());
    }

    /**
     * @param connectionManager the source of connections; injectable so a test can point this DAO
     *                          at a test schema
     */
    public JdbcTreatmentDAO(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    // ------------------------------------------------------------------ single-row reads

    @Override
    public Optional<Treatment> findById(int treatmentId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {

            statement.setInt(1, treatmentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapTreatment(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading treatment " + treatmentId);
        }
    }

    @Override
    public Optional<Treatment> findByCode(String treatmentCode) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_CODE)) {

            statement.setString(1, treatmentCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapTreatment(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Loading treatment by code");
        }
    }

    // ------------------------------------------------------------------ list reads

    @Override
    public List<Treatment> findAllActive() {
        List<Treatment> treatments = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                treatments.add(mapTreatment(resultSet));
            }
            return treatments;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Listing active treatments");
        }
    }

    @Override
    public List<Treatment> findAll() {
        List<Treatment> treatments = new ArrayList<>();

        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                treatments.add(mapTreatment(resultSet));
            }
            return treatments;

        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "Listing all treatments");
        }
    }

    // ------------------------------------------------------------------ checks

    @Override
    public boolean isActive(int treatmentId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_ACTIVE)) {

            statement.setInt(1, treatmentId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e,
                    "Checking treatment " + treatmentId + " is active");
        }
    }

    // ------------------------------------------------------------------ row mapping

    /**
     * Copies the current row of {@code treatments} into a {@link Treatment}.
     *
     * <p>{@code base_cost} is read with {@code getBigDecimal} so the price reaches the billing
     * decorators at full decimal precision.</p>
     */
    private static Treatment mapTreatment(ResultSet resultSet) throws SQLException {
        Treatment treatment = new Treatment();
        treatment.setTreatmentId(resultSet.getInt("treatment_id"));
        treatment.setTreatmentCode(resultSet.getString("treatment_code"));
        treatment.setTreatmentName(resultSet.getString("treatment_name"));
        treatment.setDescription(resultSet.getString("description"));
        treatment.setBaseCost(resultSet.getBigDecimal("base_cost"));
        treatment.setDurationMinutes(resultSet.getInt("duration_minutes"));
        treatment.setTaxable(resultSet.getBoolean("is_taxable"));
        treatment.setActive(resultSet.getBoolean("is_active"));
        return treatment;
    }
}
