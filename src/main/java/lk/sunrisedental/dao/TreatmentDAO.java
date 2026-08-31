package lk.sunrisedental.dao;

import lk.sunrisedental.model.Treatment;

import java.util.List;
import java.util.Optional;

/**
 * Data access for the treatment catalogue, which is also the price list.
 *
 * <p>This is the only route by which a treatment price enters the application. The billing
 * engine reaches it through the Bridge's pricing provider rather than calling here directly, which
 * is what allows the whole billing calculation to be unit-tested against an in-memory price table
 * with no database at all.</p>
 */
public interface TreatmentDAO {

    /**
     * @param treatmentId the primary key
     * @return the treatment, or empty if not found
     */
    Optional<Treatment> findById(int treatmentId);

    /**
     * @param treatmentCode the business code, for example {@code RCT-01}
     * @return the treatment, or empty if not found
     */
    Optional<Treatment> findByCode(String treatmentCode);

    /** @return treatments currently offered, ordered by name; the booking dropdown. */
    List<Treatment> findAllActive();

    /** @return every treatment including withdrawn ones, so historical bills still resolve. */
    List<Treatment> findAll();

    /**
     * @param treatmentId the primary key
     * @return {@code true} if that treatment exists and is currently offered
     */
    boolean isActive(int treatmentId);
}
