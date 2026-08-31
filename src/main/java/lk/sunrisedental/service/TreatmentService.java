package lk.sunrisedental.service;

import lk.sunrisedental.dao.TreatmentDAO;
import lk.sunrisedental.exception.BusinessRuleException;
import lk.sunrisedental.model.Treatment;
import lk.sunrisedental.patterns.singleton.ConfigurationManager;
import lk.sunrisedental.util.Money;

import java.math.BigDecimal;
import java.util.List;

/**
 * The treatment catalogue and price list.
 *
 * <p>Read-only by design. A treatment's price is referenced by every bill ever issued for it, so
 * changing one is a controlled data-administration task carried out against {@code treatments}
 * directly, not a screen the front desk can reach. Bills store their own copy of the figures, so a
 * later price change never alters a receipt already given to a patient - but the discipline of
 * keeping price edits out of the running application is what makes that guarantee easy to reason
 * about.</p>
 *
 * <p>{@link #indicativeTotal(Treatment)} exists so the treatments screen can answer "what will this
 * cost me?" without pretending to be a bill. It shows the list price plus the consultation fee and
 * levy that would normally apply; the authoritative figure always comes from
 * {@code BillingService}, which knows whether the visit qualifies as a follow-up.</p>
 */
public class TreatmentService {

    private final TreatmentDAO treatmentDAO;
    private final ConfigurationManager configuration;

    public TreatmentService(TreatmentDAO treatmentDAO, ConfigurationManager configuration) {
        this.treatmentDAO = treatmentDAO;
        this.configuration = configuration;
    }

    /** @return every treatment, including withdrawn ones, so historical bills still resolve. */
    public List<Treatment> findAll() {
        return treatmentDAO.findAll();
    }

    /** @return treatments currently offered, for the booking dropdown. */
    public List<Treatment> findAllActive() {
        return treatmentDAO.findAllActive();
    }

    /**
     * @param treatmentId the treatment
     * @return the treatment
     * @throws BusinessRuleException if no such treatment exists
     */
    public Treatment findById(int treatmentId) {
        return treatmentDAO.findById(treatmentId)
                .orElseThrow(() -> new BusinessRuleException("TREATMENT_NOT_FOUND",
                        "No treatment record was found."));
    }

    /** @return the consultation fee added once to every bill. */
    public BigDecimal consultationFee() {
        return configuration.getConsultationFee();
    }

    /** @return the health service levy percentage applied to taxable treatments. */
    public BigDecimal taxRatePercent() {
        return configuration.getTaxRatePercent();
    }

    /**
     * @param treatment the treatment to price
     * @return the list price plus consultation fee, plus the levy if the treatment is taxable.
     *         An indication for the price list only - never a substitute for an issued bill.
     */
    public BigDecimal indicativeTotal(Treatment treatment) {
        BigDecimal charges = Money.sum(treatment.getBaseCost(), configuration.getConsultationFee());

        if (!treatment.isTaxable()) {
            return charges;
        }
        return Money.sum(charges,
                Money.percentOf(charges, configuration.getTaxRatePercent()));
    }
}
