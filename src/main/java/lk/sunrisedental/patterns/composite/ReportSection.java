package lk.sunrisedental.patterns.composite;

import lk.sunrisedental.util.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * COMPOSITE - a branch.
 *
 * <p>Holds children that may themselves be sections or lines, to any depth. A section is the root
 * of a whole report, a per-dentist grouping inside it, or a sub-grouping inside that; nothing in
 * this class knows or cares which.</p>
 *
 * <p><strong>Totals are derived, never accumulated.</strong> {@link #getTotal()} walks its
 * children every time it is asked. That is the property worth having: a subtotal cannot fall out
 * of step with the rows printed beneath it, because it is computed from exactly those rows. The
 * usual alternative - a running total updated as rows are added - is correct only as long as
 * nobody adds a row by another route, and the clinic's problem was reports that did not agree
 * with each other.</p>
 *
 * <p>The cost is recomputation on each call, which for reports of a few hundred rows is
 * irrelevant next to the query that produced them.</p>
 */
public class ReportSection implements ReportComponent {

    private final String title;
    private final String subtitle;
    private final List<ReportComponent> children = new ArrayList<>();
    private int depth;

    /**
     * @param title    the section heading
     * @param subtitle a secondary descriptor, or null
     */
    public ReportSection(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    public ReportSection(String title) {
        this(title, null);
    }

    /**
     * Adds a child and sets its depth.
     *
     * @param child a line or another section
     * @return this section, so a tree can be built fluently
     */
    public ReportSection add(ReportComponent child) {
        Objects.requireNonNull(child, "child");
        child.setDepth(depth + 1);
        children.add(child);
        return this;
    }

    /**
     * Adds several children.
     *
     * @param newChildren the children to add
     * @return this section
     */
    public ReportSection addAll(List<? extends ReportComponent> newChildren) {
        newChildren.forEach(this::add);
        return this;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getSubtitle() {
        return subtitle;
    }

    /** @return the sum of every descendant, computed by recursion. */
    @Override
    public BigDecimal getTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (ReportComponent child : children) {
            total = total.add(child.getTotal());
        }
        return Money.scale(total);
    }

    /** @return the number of leaves beneath this section; zero for an empty section. */
    @Override
    public int getCount() {
        int count = 0;
        for (ReportComponent child : children) {
            count += child.getCount();
        }
        return count;
    }

    @Override
    public List<ReportComponent> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    /** @return {@code true} if this section has no children, so the view can show an empty state. */
    public boolean isEmpty() {
        return children.isEmpty();
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public void setDepth(int depth) {
        this.depth = depth;
        // Keep the whole subtree consistent when a built section is later attached to a parent.
        for (ReportComponent child : children) {
            child.setDepth(depth + 1);
        }
    }

    /**
     * Flattens the tree into the order a table renders it: each node followed by its descendants.
     *
     * @return every node in the subtree, this section first
     */
    public List<ReportComponent> flatten() {
        List<ReportComponent> flat = new ArrayList<>();
        collect(this, flat);
        return flat;
    }

    private static void collect(ReportComponent node, List<ReportComponent> into) {
        into.add(node);
        for (ReportComponent child : node.getChildren()) {
            collect(child, into);
        }
    }

    @Override
    public String toString() {
        return "ReportSection{" + title + ", children=" + children.size()
                + ", total=" + getTotal() + '}';
    }
}
