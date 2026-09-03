package lk.sunrisedental.controller.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Adds the response headers that constrain what a browser will do with these pages.
 *
 * <p>Each header answers a specific attack, and none of them costs anything:</p>
 *
 * <ul>
 *   <li>{@code Content-Security-Policy} restricts every resource to this origin and forbids inline
 *       and external script outright. The application contains no JavaScript at all, so this policy
 *       breaks nothing - and it means that even if an escaping mistake let a script tag through a
 *       patient name, the browser would refuse to run it.</li>
 *   <li>{@code X-Frame-Options: DENY} stops the application being framed by another site, which is
 *       what clickjacking needs.</li>
 *   <li>{@code X-Content-Type-Options: nosniff} stops a browser second-guessing a declared content
 *       type - the route by which an uploaded or reflected value gets executed as script.</li>
 *   <li>{@code Referrer-Policy} keeps appointment numbers and patient ids out of the
 *       {@code Referer} header sent to any external site a user navigates to.</li>
 * </ul>
 *
 * <p>Signed-in pages are additionally marked no-store, so a patient record is not left in the disk
 * cache of a shared front-desk machine for the next person to reach with the back button.</p>
 */
public class SecurityHeadersFilter implements Filter {

    private static final String CSP =
            "default-src 'self'; script-src 'none'; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader("Content-Security-Policy", CSP);
            httpResponse.setHeader("X-Content-Type-Options", "nosniff");
            httpResponse.setHeader("X-Frame-Options", "DENY");
            httpResponse.setHeader("Referrer-Policy", "same-origin");

            if (request instanceof HttpServletRequest httpRequest && isProtected(httpRequest)) {
                httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
                httpResponse.setHeader("Pragma", "no-cache");
            }
        }
        chain.doFilter(request, response);
    }

    /** @return {@code true} for pages that can only be seen while signed in. */
    private static boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/app/") || path.startsWith("/api/");
    }
}
