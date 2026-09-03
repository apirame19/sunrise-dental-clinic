package lk.sunrisedental.patterns.bridge;

/**
 * The facts a billing policy needs in order to decide what to charge.
 *
 * <p>Kept to the minimum. The calculators receive what determines the charge and nothing else -
 * no patient name, no address, no appointment object. That is what allows every billing test to
 * construct a context in one line, and it means a change to the appointment model cannot ripple
 * into the billing engine.</p>
 *
 * <p>Whether a visit qualifies as a follow-up is decided by {@code BillingService} from the
 * patient's history, not here. Deciding it here would drag appointment queries into what is
 * otherwise pure arithmetic.</p>
 *
 * @param treatmentId the treatment being billed
 * @param followUp    whether this visit qualifies for follow-up terms
 */
public record BillingContext(int treatmentId, boolean followUp) {
}
