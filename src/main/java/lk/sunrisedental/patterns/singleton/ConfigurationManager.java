package lk.sunrisedental.patterns.singleton;

import lk.sunrisedental.dao.ConfigDAO;
import lk.sunrisedental.dao.impl.JdbcConfigDAO;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SINGLETON - the application's one cache of clinic settings.
 *
 * <p><strong>Why a Singleton and not a static utility.</strong> The consultation fee, tax rate,
 * opening hours and discount rules are read on every bill and every availability check. Querying
 * {@code app_config} each time would put the most frequently needed values on the slowest path.
 * One cache, loaded once, shared by every request - and, critically, one place to invalidate when
 * an administrator changes a fee, which a scattering of static finals could never offer.</p>
 *
 * <p>Uses the same initialisation-on-demand holder idiom as
 * {@link DatabaseConnectionManager}: lazy, thread-safe, no synchronisation on the read path.</p>
 *
 * <p>Every getter takes a fallback. If the database is unreachable or a key has been deleted, the
 * clinic still bills at a sane rate and the opening-hours check still works, rather than the whole
 * application failing because one settings row is missing. Missing keys are logged so the gap is
 * visible rather than silent.</p>
 */
public final class ConfigurationManager {

    private static final Logger LOGGER = Logger.getLogger(ConfigurationManager.class.getName());

    // ---- keys, named once so a typo cannot silently return the fallback forever ----
    public static final String CLINIC_NAME              = "clinic.name";
    public static final String CLINIC_ADDRESS           = "clinic.address";
    public static final String CLINIC_PHONE            = "clinic.phone";
    public static final String CURRENCY_CODE           = "currency.code";
    public static final String CONSULTATION_FEE        = "consultation.fee";
    public static final String TAX_RATE_PERCENT        = "tax.rate.percent";
    public static final String DISCOUNT_FOLLOWUP_PCT   = "discount.followup.percent";
    public static final String DISCOUNT_FOLLOWUP_DAYS  = "discount.followup.days";
    public static final String CLINIC_OPEN_TIME        = "clinic.open.time";
    public static final String CLINIC_CLOSE_TIME       = "clinic.close.time";
    public static final String CLINIC_SLOT_MINUTES     = "clinic.slot.minutes";
    public static final String CLINIC_CLOSED_WEEKDAY   = "clinic.closed.weekday";
    public static final String BOOKING_MAX_DAYS_AHEAD  = "booking.max.days.ahead";
    public static final String LOGIN_MAX_ATTEMPTS      = "login.max.attempts";
    public static final String LOGIN_LOCKOUT_MINUTES   = "login.lockout.minutes";

    private final ConfigDAO configDAO;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    private static final class Holder {
        private static final ConfigurationManager INSTANCE =
                new ConfigurationManager(new JdbcConfigDAO());

        private Holder() {
        }
    }

    /** @return the single instance for this JVM. */
    public static ConfigurationManager getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * @param configDAO the settings source; injectable so tests can supply a fixed map without a
     *                  database
     */
    ConfigurationManager(ConfigDAO configDAO) {
        this.configDAO = configDAO;
    }

    /**
     * Creates an instance backed by a caller-supplied DAO.
     *
     * <p>For tests only. Production code uses {@link #getInstance()}; this exists so that billing
     * tests can pin the consultation fee and tax rate to known values and assert exact totals.</p>
     *
     * @param configDAO the settings source
     * @return a standalone manager that does not touch the shared instance
     */
    public static ConfigurationManager forTesting(ConfigDAO configDAO) {
        return new ConfigurationManager(configDAO);
    }

    /** Discards the cache so the next read reloads from the database. */
    public synchronized void reload() {
        loaded = false;
        cache.clear();
        ensureLoaded();
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (loaded) {
                return;
            }
            try {
                cache.putAll(configDAO.loadAll());
            } catch (RuntimeException e) {
                // Fallbacks keep the clinic working; failing every bill because a settings
                // query failed would be a far worse outcome than billing at the default rate.
                LOGGER.log(Level.SEVERE,
                        "Could not load application configuration; falling back to defaults", e);
            }
            loaded = true;
        }
    }

    /**
     * @param key      the setting name
     * @param fallback the value to use if the setting is absent
     * @return the configured value, or {@code fallback}
     */
    public String getString(String key, String fallback) {
        ensureLoaded();
        String value = cache.get(key);
        if (value == null) {
            LOGGER.warning(() -> "Configuration key '" + key + "' is missing; using " + fallback);
            return fallback;
        }
        return value;
    }

    /**
     * @param key      the setting name
     * @param fallback the value to use if absent or unparseable
     * @return the configured monetary or percentage value
     */
    public BigDecimal getDecimal(String key, BigDecimal fallback) {
        String raw = getString(key, null);
        if (raw == null) {
            return fallback;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning(() -> "Configuration key '" + key + "' is not a number: " + raw);
            return fallback;
        }
    }

    /**
     * @param key      the setting name
     * @param fallback the value to use if absent or unparseable
     * @return the configured whole number
     */
    public int getInt(String key, int fallback) {
        String raw = getString(key, null);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning(() -> "Configuration key '" + key + "' is not an integer: " + raw);
            return fallback;
        }
    }

    /**
     * @param key      the setting name
     * @param fallback the value to use if absent or unparseable
     * @return the configured time of day
     */
    public LocalTime getTime(String key, LocalTime fallback) {
        String raw = getString(key, null);
        if (raw == null) {
            return fallback;
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (RuntimeException e) {
            LOGGER.warning(() -> "Configuration key '" + key + "' is not a time: " + raw);
            return fallback;
        }
    }

    // ---- convenience accessors for the values used most often ----------------------

    /** @return the consultation fee added once to every bill. */
    public BigDecimal getConsultationFee() {
        return getDecimal(CONSULTATION_FEE, new BigDecimal("1500.00"));
    }

    /** @return the health service levy percentage applied to taxable treatments. */
    public BigDecimal getTaxRatePercent() {
        return getDecimal(TAX_RATE_PERCENT, new BigDecimal("2.50"));
    }

    /** @return the percentage discount given for a qualifying follow-up visit. */
    public BigDecimal getFollowUpDiscountPercent() {
        return getDecimal(DISCOUNT_FOLLOWUP_PCT, new BigDecimal("10.00"));
    }

    /** @return how many days after a visit a return counts as a follow-up. */
    public int getFollowUpWindowDays() {
        return getInt(DISCOUNT_FOLLOWUP_DAYS, 30);
    }

    /** @return the earliest an appointment may start. */
    public LocalTime getOpeningTime() {
        return getTime(CLINIC_OPEN_TIME, LocalTime.of(8, 0));
    }

    /** @return the latest an appointment may finish. */
    public LocalTime getClosingTime() {
        return getTime(CLINIC_CLOSE_TIME, LocalTime.of(20, 0));
    }

    /** @return the minute boundary appointment start times must fall on. */
    public int getSlotMinutes() {
        return getInt(CLINIC_SLOT_MINUTES, 30);
    }

    /** @return the ISO day of week the clinic is closed; 7 is Sunday. */
    public int getClosedWeekday() {
        return getInt(CLINIC_CLOSED_WEEKDAY, 7);
    }

    /** @return how far ahead an appointment may be booked. */
    public int getMaxBookingDaysAhead() {
        return getInt(BOOKING_MAX_DAYS_AHEAD, 180);
    }

    /** @return failed sign-in attempts permitted before an account locks. */
    public int getMaxLoginAttempts() {
        return getInt(LOGIN_MAX_ATTEMPTS, 5);
    }

    /** @return how long an account stays locked, in minutes. */
    public int getLockoutMinutes() {
        return getInt(LOGIN_LOCKOUT_MINUTES, 15);
    }

    /** @return the clinic name printed on bills. */
    public String getClinicName() {
        return getString(CLINIC_NAME, "Sunrise Dental Clinic");
    }

    /** @return the clinic address printed on bills. */
    public String getClinicAddress() {
        return getString(CLINIC_ADDRESS, "");
    }

    /** @return the clinic telephone number printed on bills. */
    public String getClinicPhone() {
        return getString(CLINIC_PHONE, "");
    }

    /** @return the ISO currency code for all monetary display. */
    public String getCurrencyCode() {
        return getString(CURRENCY_CODE, "LKR");
    }

    /** @return an unmodifiable view of every cached setting, for the help and admin screens. */
    public Map<String, String> getAllSettings() {
        ensureLoaded();
        return Collections.unmodifiableMap(cache);
    }
}
