package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.model.Reminder;

import java.io.IOException;
import java.util.List;

/**
 * The reminder queue: {@code GET /app/reminders}.
 *
 * <p>The queue is derived from appointment data every time it is asked for. There is no scheduler,
 * no background thread and no mail server: a reminder here is a query over records the system
 * already holds, presented as a worklist. That was a deliberate choice - a reminder feature
 * depending on SMTP credentials cannot be tested in CI and stops working silently the first time
 * mail configuration drifts, which is the worst possible failure mode for something whose job is
 * to stop things being forgotten.</p>
 *
 * <p>Two kinds of entry appear, and the overdue ones sort first because they are actively blocking
 * work: an appointment whose slot has passed with no outcome recorded cannot be billed and is
 * missing from every report that counts completed visits.</p>
 */
@WebServlet(name = "ReminderServlet", urlPatterns = {"/app/reminders", "/app/reminders/*"})
public class ReminderServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    /** Bounded so a mistyped query string cannot ask for a decade of appointments. */
    private static final int MAX_LOOK_AHEAD_DAYS = 30;
    private static final int DEFAULT_LOOK_AHEAD_DAYS = 1;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!action(request).isEmpty()) {
            throw badRequest("There is no reminder page at that address.");
        }

        int lookAhead = Math.min(MAX_LOOK_AHEAD_DAYS,
                Math.max(1, intParam(request, "days", DEFAULT_LOOK_AHEAD_DAYS)));

        List<Reminder> reminders = facade().reminderQueue(currentUser(request), lookAhead);

        request.setAttribute("reminders", reminders);
        request.setAttribute("lookAheadDays", lookAhead);
        request.setAttribute("overdueCount", reminders.stream().filter(Reminder::isOverdue).count());

        render(request, response, "reminders/queue", "Reminder queue", "/app/reminders");
    }
}
