package com.scrimchic.seedchecker.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoundedLruCacheTest {

    @Test
    void storesAndReadsBack() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<String, Integer>(4);
        cache.put("a", 1);

        assertEquals(Integer.valueOf(1), cache.get("a"));
        assertTrue(cache.containsKey("a"));
        assertNull(cache.get("missing"));
        assertEquals(1, cache.size());
    }

    @Test
    void neverGrowsPastItsBound() {
        BoundedLruCache<Integer, Integer> cache = new BoundedLruCache<Integer, Integer>(8);
        for (int i = 0; i < 1000; i++) {
            cache.put(Integer.valueOf(i), Integer.valueOf(i));
            assertTrue(cache.size() <= 8, "size " + cache.size());
        }
        assertEquals(8, cache.size());
    }

    @Test
    void evictsTheLeastRecentlyUsedEntry() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<String, Integer>(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        // Touch a, so b becomes the oldest use.
        assertEquals(Integer.valueOf(1), cache.get("a"));
        cache.put("d", 4);

        assertTrue(cache.containsKey("a"));
        assertFalse(cache.containsKey("b"));
        assertTrue(cache.containsKey("c"));
        assertTrue(cache.containsKey("d"));
    }

    @Test
    void reinsertingRefreshesRatherThanDuplicating() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<String, Integer>(2);
        cache.put("a", 1);
        cache.put("a", 2);

        assertEquals(1, cache.size());
        assertEquals(Integer.valueOf(2), cache.get("a"));
    }

    @Test
    void clearEmptiesIt() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<String, Integer>(2);
        cache.put("a", 1);
        cache.clear();

        assertEquals(0, cache.size());
        assertNull(cache.get("a"));
    }

    @Test
    void rejectsANonPositiveBound() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedLruCache<String, String>(0));
        assertThrows(IllegalArgumentException.class, () -> new BoundedLruCache<String, String>(-1));
    }
}
