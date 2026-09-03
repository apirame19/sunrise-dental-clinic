package lk.sunrisedental.patterns.bridge;

import java.math.BigDecimal;

/**
 * What the pricing implementor knows about one treatment.
 *
 * <p>Deliberately narrower than {@code Treatment}: billing needs the code, the name for the
 * receipt line, the cost and whether the levy applies. It has no business knowing the duration or
 * the active flag, and keeping those out means the in-memory implementor used by the tests does
 * not have to invent values for fields nothing reads.</p>
 *
 * @param treatmentId the primary key
 * @param code        the business code, for example {@code RCT-01}
 * @param name        the name printed on the receipt
 * @param baseCost    the list price
 * @param taxable     whether the health service levy applies; preventive care is exempt
 */
public record TreatmentPrice(int treatmentId, String code, String name,
                             BigDecimal baseCost, boolean taxable) {

    /** @return the treatment as it should read on a receipt line. */
    public String describe() {
        return name + " (" + code + ")";
    }
}
