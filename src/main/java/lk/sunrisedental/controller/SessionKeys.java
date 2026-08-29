package lk.sunrisedental.controller;

/**
 * The names under which things are stored in the session and in request scope.
 *
 * <p>Named once because the same string has to match in a servlet that writes it, a filter that
 * reads it and a JSP that renders it. A typo produces a page that silently shows nothing, which is
 * far harder to notice than a compile error.</p>
 */
public final class SessionKeys {

    /** The signed-in {@code User}. Its presence is what "signed in" means. */
    public static final String USER = "sessionUser";

    /** The synchroniser token compared against every state-changing form submission. */
    public static final String CSRF_TOKEN = "csrfToken";

    /** Where the user was heading when they were sent to the login page. */
    public static final String REDIRECT_AFTER_LOGIN = "redirectAfterLogin";

    /** A one-shot success message, surviving exactly one redirect. */
    public static final String FLASH_SUCCESS = "flashSuccess";

    /** A one-shot failure message, surviving exactly one redirect. */
    public static final String FLASH_ERROR = "flashError";

    // ---- request-scope attributes shared by the layout fragments -------------------

    /** The role-filtered navigation tree, set on every rendered page. */
    public static final String NAV = "nav";

    /** The page heading. */
    public static final String PAGE_TITLE = "pageTitle";

    /** The navigation entry to mark as current. */
    public static final String ACTIVE_NAV = "activeNav";

    /** Clinic settings, for the footer and currency formatting. */
    public static final String CLINIC = "clinic";

    private SessionKeys() {
    }
}
