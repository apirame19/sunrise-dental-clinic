package lk.sunrisedental.model;

/**
 * The kind of reminder shown on the dashboard queue.
 *
 * <p>Each carries a priority used to order the queue. Overdue outcomes come first because an
 * appointment left in limbo is actively blocking something: it cannot be billed, and it distorts
 * every report that counts completed visits.</p>
 */
public enum ReminderType {

    /** The slot has passed and nobody has recorded whether the patient attended. */
    AWAITING_OUTCOME("Awaiting outcome", 1, "Record whether the patient attended"),

    /** Still scheduled and due shortly. */
    UPCOMING("Upcoming", 2, "Confirm this appointment with the patient");

    private final String label;
    private final int priority;
    private final String actionHint;

    ReminderType(String label, int priority, String actionHint) {
        this.label = label;
        this.priority = priority;
        this.actionHint = actionHint;
    }

    /** @return a human-readable label for display. */
    public String getLabel() {
        return label;
    }

    /** @return the sort order; lower is more urgent. */
    public int getPriority() {
        return priority;
    }

    /** @return what staff should do about it. */
    public String getActionHint() {
        return actionHint;
    }
}
