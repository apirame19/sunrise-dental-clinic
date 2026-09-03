package lk.sunrisedental.util;

/**
 * Escapes text for safe inclusion in HTML.
 *
 * <p>The JSP views escape with JSTL's {@code <c:out>}, which is the right tool there. This class
 * exists for the handful of places that write HTML from Java - the filters, which refuse a request
 * before any view is reached and therefore cannot forward to one.</p>
 *
 * <p>Five characters are replaced rather than the usual three. {@code "} and {@code '} matter
 * because escaped text is sometimes placed inside an attribute, and a value that is safe between
 * tags but not inside {@code value="..."} is a defect waiting for the first person who moves it.</p>
 */
public final class Html {

    private Html() {
    }

    /**
     * @param raw the text to escape; null becomes the empty string
     * @return the text with HTML-significant characters replaced by entities
     */
    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
