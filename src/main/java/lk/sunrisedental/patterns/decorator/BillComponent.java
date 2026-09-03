package lk.sunrisedental.patterns.decorator;

import lk.sunrisedental.model.BillLine;

import java.math.BigDecimal;
import java.util.List;

/**
 * DECORATOR - the component interface.
 *
 * <p>Everything on a bill answers the same two questions: what does it come to, and how does it
 * read on the receipt. A base charge and a charge wrapped in three decorators are
 * indistinguishable to a caller, which is what lets {@code BillAssembler} build whatever
 * combination applies and then treat the result as a single thing.</p>
 *
 * <p>{@link #getLines()} returns every line accumulated so far, not just this component's own.
 * The itemised receipt is therefore produced by the same traversal that produces the total, so the
 * printed lines cannot disagree with the amount printed beneath them.</p>
 */
public interface BillComponent {

    /** @return the total of this component and everything it wraps, scaled to two places. */
    BigDecimal getAmount();

    /**
     * @return the receipt lines for this component and everything it wraps, in receipt order,
     *         numbered from one
     */
    List<BillLine> getLines();

    /** @return a short description of this component, used in diagnostics. */
    String getDescription();
}
