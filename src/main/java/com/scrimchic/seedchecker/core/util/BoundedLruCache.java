package com.scrimchic.seedchecker.core.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fixed-capacity cache that evicts whatever was used longest ago.
 *
 * <p>{@link LinkedHashMap} in access order already is an LRU; all this adds is the eviction bound
 * and a narrow API, which is the whole point - a cache framework would be more machinery than the
 * problem deserves.
 *
 * <p>Not synchronised. Callers that share one across threads must do their own locking.
 */
public final class BoundedLruCache<K, V> {

    private final int maxEntries;
    private final LinkedHashMap<K, V> entries;

    public BoundedLruCache(final int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive, was " + maxEntries);
        }
        this.maxEntries = maxEntries;
        // Initial capacity sized so the map never rehashes at its bound, access-ordered so that
        // get() counts as use.
        this.entries = new LinkedHashMap<K, V>(Math.max(16, maxEntries * 4 / 3 + 1), 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public int maxEntries() {
        return maxEntries;
    }

    /** @return the value, or {@code null}; counts as a use. */
    public V get(K key) {
        return entries.get(key);
    }

    public boolean containsKey(K key) {
        return entries.containsKey(key);
    }

    public void put(K key, V value) {
        entries.put(key, value);
    }

    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
