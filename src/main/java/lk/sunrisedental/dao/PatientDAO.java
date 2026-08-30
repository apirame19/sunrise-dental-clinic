package lk.sunrisedental.dao;

import lk.sunrisedental.model.Patient;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

/**
 * Data access for patients.
 *
 * <p>{@link #insert(Patient, Connection)} takes a caller-supplied connection so that creating a
 * patient and their first appointment happens in one transaction. Registering a patient and then
 * failing to book them would leave an orphan record that nobody looks at again - precisely the
 * kind of quiet data decay the clinic asked to be rid of.</p>
 */
public interface PatientDAO {

    /**
     * @param patientId the primary key
     * @return the patient, or empty if not found
     */
    Optional<Patient> findById(int patientId);

    /**
     * Finds an existing patient by the pair that identifies them.
     *
     * <p>Used during appointment registration so that a returning patient is reused rather than
     * duplicated. A household may share one telephone number, so the number alone is not enough.</p>
     *
     * @param patientName   the patient's name
     * @param contactNumber the normalised contact number
     * @return the patient, or empty if this is someone new
     */
    Optional<Patient> findByNameAndContact(String patientName, String contactNumber);

    /** As {@link #findByNameAndContact}, on a caller-supplied transactional connection. */
    Optional<Patient> findByNameAndContact(Connection connection, String patientName,
                                           String contactNumber);

    /**
     * Free-text search across name and contact number.
     *
     * @param searchTerm partial name or number; matched case-insensitively
     * @return matching patients, ordered by name
     */
    List<Patient> search(String searchTerm);

    /** @return every patient, ordered by name. */
    List<Patient> findAll();

    /**
     * Inserts a patient within an existing transaction.
     *
     * @param patient    the patient to store
     * @param connection the transactional connection
     * @return the generated patient id
     */
    int insert(Patient patient, Connection connection);

    /**
     * Inserts a patient in its own transaction.
     *
     * @param patient the patient to store
     * @return the generated patient id
     */
    int insert(Patient patient);

    /**
     * Updates a patient's contact details.
     *
     * @param patient the patient carrying the new values and an existing id
     * @return {@code true} if a row was changed
     */
    boolean update(Patient patient);

    /** @return the total number of registered patients. */
    long count();

    /**
     * Finds the patient record a login belongs to.
     *
     * <p>This is what makes a patient portal possible: the session carries a {@code User}, and the
     * patient's own appointments can only be found once that account has been resolved to the
     * {@code patients} row it was bound to at registration.</p>
     *
     * @param userId the account
     * @return the patient record, or empty if this account is not a patient's
     */
    Optional<Patient> findByUserId(int userId);

    /**
     * Binds a patient record to a login, inside an existing transaction.
     *
     * @param connection the transactional connection
     * @param patientId  the patient record
     * @param userId     the account it belongs to
     * @return {@code true} if a row was changed
     */
    boolean linkToUser(Connection connection, int patientId, int userId);
}
