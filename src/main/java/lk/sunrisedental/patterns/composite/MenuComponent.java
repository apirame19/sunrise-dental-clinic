package lk.sunrisedental.patterns.composite;

import lk.sunrisedental.model.Role;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * COMPOSITE - the navigation menu, the pattern's second genuine use.
 *
 * <p>The menu is a real tree: <em>Reports → Daily / Dentist workload / Revenue / Billing
 * summary</em>. A single class covers both an item and a group, so the JSP renders the whole
 * navigation with one recursive fragment regardless of how deeply it nests.</p>
 *
 * <p><strong>Role filtering is why this is worth modelling as a tree rather than hard-coding the
 * markup.</strong> {@link #filterFor(Role)} returns a pruned copy: entries the role may not use
 * disappear, and a group left with no children disappears with them, so a receptionist never sees
 * an empty "Reports" heading. Because the same {@link Role} predicates drive both this and the
 * authorisation filter, a menu item can never be shown to someone who would be refused on
 * clicking it - the two cannot drift apart, because there is only one source of truth.</p>
 */
public class MenuComponent {

    private final String label;
    private final String url;
    private final String icon;
    private final Predicate<Role> visibleTo;
    private final List<MenuComponent> children = new ArrayList<>();

    private MenuComponent(String label, String url, String icon, Predicate<Role> visibleTo) {
        this.label = label;
        this.url = url;
        this.icon = icon;
        this.visibleTo = visibleTo;
    }

    /**
     * Creates a clickable item.
     *
     * @param label     the text shown
     * @param url       the target, relative to the context path
     * @param icon      a short text marker shown before the label
     * @param visibleTo which roles may see it
     */
    public static MenuComponent item(String label, String url, String icon,
                                     Predicate<Role> visibleTo) {
        return new MenuComponent(label, url, icon, visibleTo);
    }

    /**
     * Creates a group, which has no target of its own.
     *
     * @param label the heading
     * @param icon  a short text marker
     */
    public static MenuComponent group(String label, String icon) {
        return new MenuComponent(label, null, icon, role -> true);
    }

    /**
     * Adds a child.
     *
     * @param child an item or a nested group
     * @return this component, so a menu can be declared fluently
     */
    public MenuComponent add(MenuComponent child) {
        children.add(child);
        return this;
    }

    /**
     * Prunes the tree to what a role may use.
     *
     * @param role the signed-in user's role
     * @return a copy containing only permitted entries, with empty groups removed, or
     *         {@code null} if nothing in this subtree is visible to that role
     */
    public MenuComponent filterFor(Role role) {
        if (isItem()) {
            return visibleTo.test(role) ? this : null;
        }

        MenuComponent copy = new MenuComponent(label, url, icon, visibleTo);
        for (MenuComponent child : children) {
            MenuComponent filtered = child.filterFor(role);
            if (filtered != null) {
                copy.children.add(filtered);
            }
        }
        // A group whose children were all removed is itself removed, so the sidebar never shows
        // a heading that leads nowhere.
        return copy.children.isEmpty() ? null : copy;
    }

    /** @return {@code true} if this is a clickable item rather than a group. */
    public boolean isItem() {
        return url != null;
    }

    public String getLabel() {
        return label;
    }

    public String getUrl() {
        return url;
    }

    public String getIcon() {
        return icon;
    }

    /** @return the children, unmodifiable; empty for an item. */
    public List<MenuComponent> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public String toString() {
        return "MenuComponent{" + label + (isItem() ? " -> " + url : " (" + children.size() + ")") + '}';
    }
}
