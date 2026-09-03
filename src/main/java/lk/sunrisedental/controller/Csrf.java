package lk.sunrisedental.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Cross-site request forgery protection: a per-session synchroniser token.
 *
 * <p><strong>Why this is needed even though the application has no JavaScript.</strong> The threat
 * is not a script on this site; it is a form on somebody else's. A page a signed-in receptionist
 * visits elsewhere can contain a hidden form that posts to
 * {@code /app/appointments/status} - the browser attaches the session cookie automatically, and
 * without a token the request is indistinguishable from a real one. Cancelling appointments or
 * deactivating staff accounts from another site is exactly the kind of thing that must not be
 * possible.</p>
 *
 * <p>The token is a 32-byte {@link SecureRandom} value held in the session and echoed by every
 * state-changing form as a hidden field. An attacker's page cannot read it, because the same-origin
 * policy stops it reading any response from this application.</p>
 *
 * <p>Comparison uses {@link MessageDigest#isEqual}, which does not return early on the first
 * differing byte. The timing leak here is small, but constant-time comparison costs nothing.</p>
 */
public final class Csrf {

    /** The hidden input name carried by every state-changing form. */
    public static final String FIELD = "csrfToken";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private Csrf() {
    }

    /**
     * Returns the session's token, creating one on first use.
     *
     * @param session the current session; must not be null
     * @return the token to embed in a form
     */
    public static String token(HttpSession session) {
        Object existing = session.getAttribute(SessionKeys.CSRF_TOKEN);
        if (existing instanceof String token && !token.isEmpty()) {
            return token;
        }
        return regenerate(session);
    }

    /**
     * Issues a fresh token, discarding any previous one.
     *
     * <p>Called when a session is created at sign-in. A token minted before authentication must not
     * survive it, for the same reason the session id itself is regenerated: anything an attacker
     * could have planted in the pre-login session must become worthless the moment the session
     * gains authority.</p>
     *
     * @param session the session to stamp
     * @return the new token
     */
    public static String regenerate(HttpSession session) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SessionKeys.CSRF_TOKEN, token);
        return token;
    }

    /**
     * Checks a submitted token against the session's.
     *
     * @param request the submission
     * @return {@code true} only if the session holds a token and the request carries the same one
     */
    public static boolean isValid(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object expected = session.getAttribute(SessionKeys.CSRF_TOKEN);
        String submitted = request.getParameter(FIELD);

        if (!(expected instanceof String expectedToken)
                || expectedToken.isEmpty() || submitted == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                submitted.getBytes(StandardCharsets.UTF_8));
    }
}
