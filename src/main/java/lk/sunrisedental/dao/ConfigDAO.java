package lk.sunrisedental.dao;

import java.util.Map;

/**
 * Data access for the {@code app_config} settings table.
 *
 * <p>Read in bulk rather than key by key, because {@code ConfigurationManager} caches the whole set
 * once. Settings are read on every bill and every availability check; issuing a query for each
 * would put the busiest lookups in the system on the slowest path for no benefit.</p>
 */
public interface ConfigDAO {

    /** @return every setting, keyed by {@code config_key}. */
    Map<String, String> loadAll();
}
