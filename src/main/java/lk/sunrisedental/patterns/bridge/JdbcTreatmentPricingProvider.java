package lk.sunrisedental.patterns.bridge;

import lk.sunrisedental.dao.TreatmentDAO;
import lk.sunrisedental.model.Treatment;
import lk.sunrisedental.patterns.singleton.ConfigurationManager;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * BRIDGE - concrete implementor backed by the live database.
 *
 * <p>Prices come from the {@code treatments} table and rates from {@code app_config}, so an
 * administrator changing the consultation fee changes what the clinic bills without a rebuild.
 * This is what runs in production.</p>
 *
 * <p>Note that it reads treatments including withdrawn ones. Whether a treatment may still be
 * <em>booked</em> is a validation question answered by {@code TreatmentValidator}; whether it can
 * be <em>billed</em> is different, because an appointment booked last week must remain billable
 * even if the treatment was withdrawn from the menu yesterday.</p>
 */
public class JdbcTreatmentPricingProvider implements TreatmentPricingProvider {

    private final TreatmentDAO treatmentDAO;
    private final ConfigurationManager configuration;

    /**
     * @param treatmentDAO  the price list
     * @param configuration the clinic's fee and rate settings
     */
    public JdbcTreatmentPricingProvider(TreatmentDAO treatmentDAO,
                                        ConfigurationManager configuration) {
        this.treatmentDAO = treatmentDAO;
        this.configuration = configuration;
    }

    @Override
    public Optional<TreatmentPrice> findPrice(int treatmentId) {
        return treatmentDAO.findById(treatmentId).map(JdbcTreatmentPricingProvider::toPrice);
    }

    private static TreatmentPrice toPrice(Treatment treatment) {
        return new TreatmentPrice(
                treatment.getTreatmentId(),
                treatment.getTreatmentCode(),
                treatment.getTreatmentName(),
                treatment.getBaseCost(),
                treatment.isTaxable());
    }

    @Override
    public BigDecimal consultationFee() {
        return configuration.getConsultationFee();
    }

    @Override
    public BigDecimal taxRatePercent() {
        return configuration.getTaxRatePercent();
    }

    @Override
    public BigDecimal followUpDiscountPercent() {
        return configuration.getFollowUpDiscountPercent();
    }
}
