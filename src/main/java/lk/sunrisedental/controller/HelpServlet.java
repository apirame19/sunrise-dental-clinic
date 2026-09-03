package lk.sunrisedental.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.sunrisedental.model.User;

import java.io.IOException;

/**
 * The help screen: {@code GET /app/help}.
 *
 * <p>Shows the clinic's own settings - opening hours, consultation fee, levy, the booking window,
 * the lockout policy - because those are the numbers staff are asked about at the desk and they
 * are not written into the code. Reading them from {@code ConfigurationManager} means the help
 * page cannot describe rules the application is not actually applying, which is the usual fate of
 * hand-written documentation.</p>
 *
 * <p>It also states what the signed-in user's role permits, using the same {@link
 * lk.sunrisedental.model.Role} predicates the menu and the facade use. "Why can I not see the
 * revenue report" is a support question this answers without anyone having to read the source.</p>
 */
@WebServlet(name = "HelpServlet", urlPatterns = "/app/help")
public class HelpServlet extends BaseServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        User actor = currentUser(request);

        request.setAttribute("settings", facade().configuration().getAllSettings());
        request.setAttribute("role", actor.getRole());

        render(request, response, "help", "Help and clinic settings", "/app/help");
    }
}
