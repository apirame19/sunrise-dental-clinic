package lk.sunrisedental.patterns.composite;

import java.math.BigDecimal;
import java.util.List;

/**
 * COMPOSITE - the component interface for report structures.
 *
 * <p>Reports at this clinic are genuinely trees, not lists. The daily report is
 * <em>report → a section per dentist → a line per appointment</em>, and the revenue report is
 * <em>report → a section per treatment category → a line per treatment</em>. Both want a subtotal
 * at every level and a grand total at the root.</p>
 *
 * <p>Composite is what makes that a single recursive definition instead of a nest of loops with
 * running totals declared outside them. A subtotal that is computed by walking children can never
 * disagree with the lines printed under it; a subtotal accumulated in a separate variable can, and
 * that is how report totals start failing to add up.</p>
 *
 * <p>Because a section and a line answer the same questions, the JSP renders any report with one
 * recursive fragment, whatever its depth or shape.</p>
 */
public interface ReportComponent {

    /** @return the heading for a section, or the label for a line. */
    String getTitle();

    /** @return a secondary descriptor, such as a dentist's specialisation or an appointment time. */
    String getSubtitle();

    /**
     * @return the monetary total. For a line this is its own amount; for a section it is the sum
     *         of everything beneath it, computed by recursion rather than accumulated separately.
     */
    BigDecimal getTotal();

    /**
     * @return the number of records. For a line this is one; for a section it is the number of
     *         leaves beneath it, so an empty section correctly reports zero rather than one.
     */
    int getCount();

    /** @return the children of a section, or an empty list for a line. */
    List<ReportComponent> getChildren();

    /** @return {@code true} if this is a line rather than a section. */
    boolean isLeaf();

    /** @return how deep this node sits, used by the view to indent it. */
    int getDepth();

    /** Sets the depth of this node and everything beneath it. */
    void setDepth(int depth);
}
