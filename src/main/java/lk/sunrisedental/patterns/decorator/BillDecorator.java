package lk.sunrisedental.patterns.decorator;

import lk.sunrisedental.model.BillLine;
import lk.sunrisedental.model.BillLineType;
import lk.sunrisedental.util.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DECORATOR - the abstract decorator.
 *
 * <p>Wraps another {@link BillComponent} and contributes one charge of its own. Because a
 * decorator is itself a component, the wrapped thing may be the base charge or another decorator,
 * and the clinic can add a component - a late-cancellation fee, a materials surcharge - by writing
 * one class rather than by editing a growing calculation method.</p>
 *
 * <p>That extensibility is the argument for Decorator here over a single method that adds up four
 * known figures. The bill is a <em>variable</em> composition: a preventive treatment carries no
 * levy, a follow-up carries no consultation fee but does carry a discount, and an ordinary
 * restorative visit carries both. Sub-classing every combination would be combinatorial; wrapping
 * exactly the components that apply is linear.</p>
 *
 * <p>The traversal - accumulate the wrapped amount, append the wrapped lines, then add this
 * component's own - is implemented once here. Subclasses supply only their amount, their line type
 * and their wording, so none of them can forget to include what it wraps.</p>
 */
public abstract class BillDecorator implements BillComponent {

    /** The component being wrapped; never null. */
    protected final BillComponent wrapped;

    protected BillDecorator(BillComponent wrapped) {
        this.wrapped = Objects.requireNonNull(wrapped, "wrapped component");
    }

    @Override
    public BigDecimal getAmount() {
        return Money.sum(wrapped.getAmount(), ownAmount());
    }

    @Override
    public List<BillLine> getLines() {
        List<BillLine> lines = new ArrayList<>(wrapped.getLines());
        if (contributesLine()) {
            lines.add(new BillLine(lines.size() + 1, ownLineType(), ownDescription(), ownAmount()));
        }
        return lines;
    }

    @Override
    public String getDescription() {
        return ownDescription();
    }

    /**
     * @return this component's own contribution, signed. A deduction returns a negative value so
     *         that the lines on a receipt sum to its total.
     */
    protected abstract BigDecimal ownAmount();

    /** @return the kind of line this component adds. */
    protected abstract BillLineType ownLineType();

    /** @return the wording for this component's receipt line. */
    protected abstract String ownDescription();

    /**
     * @return whether this component appears on the receipt at all.
     *         A zero component is omitted rather than printed as "0.00" - a preventive receipt
     *         should not carry a tax line, and a waived consultation should not carry one either.
     */
    protected boolean contributesLine() {
        return ownAmount().signum() != 0;
    }
}
