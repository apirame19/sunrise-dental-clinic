package lk.sunrisedental.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal, dependency-free JSON serialiser for the {@code /api/*} web-service tier.
 *
 * <p>Written by hand rather than pulling in Jackson or Gson, because the agreed technology
 * stack rules out frameworks that the project does not genuinely need. The scope required
 * here is small and fixed: build an object or array, add typed members, render a string.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * String json = JsonWriter.object()
 *         .add("appointmentNo", "APT-20260815-001")
 *         .add("totalAmount", new BigDecimal("4500.00"))
 *         .add("patient", JsonWriter.object().add("name", patient.getName()))
 *         .toJson();
 * </pre>
 *
 * <p><strong>Security note.</strong> {@link #escape(String)} is a control, not a formatting
 * detail. Every value written through this class originates from user input or the database,
 * so an unescaped quote or control character would let a patient name terminate its JSON
 * string early and corrupt the response structure.</p>
 *
 * <p>Instances are not thread-safe; a writer is built and rendered within a single request.</p>
 */
public final class JsonWriter {

    private JsonWriter() {
        // Static factory only.
    }

    /** Anything that can render itself as a JSON fragment. */
    public interface JsonValue {
        String toJson();
    }

    /** Starts a new JSON object. */
    public static JsonObject object() {
        return new JsonObject();
    }

    /** Starts a new JSON array. */
    public static JsonArray array() {
        return new JsonArray();
    }

    /**
     * Escapes a raw string for safe inclusion inside a JSON string literal.
     *
     * @param raw the text to escape; may be {@code null}
     * @return the escaped text, without surrounding quotes, or {@code null} if {@code raw} was null
     */
    public static String escape(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        // Any other control character must be escaped; everything else,
                        // including non-ASCII letters, is emitted as-is because the
                        // response is always served as UTF-8.
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /** Renders a value as a JSON string literal, or the literal {@code null}. */
    private static String quoted(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    /**
     * Renders a number without quotes.
     *
     * <p>{@link BigDecimal} is rendered with {@link BigDecimal#toPlainString()} so that monetary
     * values keep their scale ({@code 1500.00} stays {@code 1500.00}) and never appear in
     * scientific notation, which downstream clients would struggle to parse as currency.</p>
     */
    private static String numeric(Number value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return value.toString();
    }

    // ------------------------------------------------------------------ object

    /** A JSON object. Members are rendered in the order they were added. */
    public static final class JsonObject implements JsonValue {

        private final List<String> members = new ArrayList<>();

        private JsonObject() {
        }

        private JsonObject put(String name, String renderedValue) {
            members.add(quoted(name) + ":" + renderedValue);
            return this;
        }

        /** Adds a string member. A {@code null} value is rendered as JSON {@code null}. */
        public JsonObject add(String name, String value) {
            return put(name, quoted(value));
        }

        /** Adds a numeric member. A {@code null} value is rendered as JSON {@code null}. */
        public JsonObject add(String name, Number value) {
            return put(name, numeric(value));
        }

        /** Adds a boolean member. */
        public JsonObject add(String name, boolean value) {
            return put(name, Boolean.toString(value));
        }

        /** Adds a nested object or array. A {@code null} value is rendered as JSON {@code null}. */
        public JsonObject add(String name, JsonValue value) {
            return put(name, value == null ? "null" : value.toJson());
        }

        /** Adds a member whose value is explicitly JSON {@code null}. */
        public JsonObject addNull(String name) {
            return put(name, "null");
        }

        @Override
        public String toJson() {
            return "{" + String.join(",", members) + "}";
        }

        @Override
        public String toString() {
            return toJson();
        }
    }

    // ------------------------------------------------------------------- array

    /** A JSON array. Elements are rendered in the order they were added. */
    public static final class JsonArray implements JsonValue {

        private final List<String> elements = new ArrayList<>();

        private JsonArray() {
        }

        /** Appends a string element. A {@code null} value is rendered as JSON {@code null}. */
        public JsonArray add(String value) {
            elements.add(quoted(value));
            return this;
        }

        /** Appends a numeric element. A {@code null} value is rendered as JSON {@code null}. */
        public JsonArray add(Number value) {
            elements.add(numeric(value));
            return this;
        }

        /** Appends a boolean element. */
        public JsonArray add(boolean value) {
            elements.add(Boolean.toString(value));
            return this;
        }

        /** Appends a nested object or array. A {@code null} value is rendered as JSON {@code null}. */
        public JsonArray add(JsonValue value) {
            elements.add(value == null ? "null" : value.toJson());
            return this;
        }

        /** @return the number of elements appended so far. */
        public int size() {
            return elements.size();
        }

        @Override
        public String toJson() {
            return "[" + String.join(",", elements) + "]";
        }

        @Override
        public String toString() {
            return toJson();
        }
    }
}
