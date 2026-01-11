## Code Review

You are reviewing the following code submitted as part of a task to implement an item cache in a highly concurrent application. The anticipated load includes: thousands of reads per second, hundreds of writes per second, tens of concurrent threads.
Your objective is to identify and explain the issues in the implementation that must be addressed before deploying the code to production. Please provide a clear explanation of each issue and its potential impact on production behaviour.

```java
import java.util.concurrent.ConcurrentHashMap;

public class SimpleCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final long ttlMs = 60000; // 1 minute

    public static class CacheEntry<V> {
        private final V value;
        private final long timestamp;

        public CacheEntry(V value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        public V getValue() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public void put(K key, V value) {
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis()));
    }

    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry != null) {
            if (System.currentTimeMillis() - entry.getTimestamp() < ttlMs) {
                return entry.getValue();
            }
        }
        return null;
    }

    public int size() {
        return cache.size();
    }
}
```

# My Review

## SimpleCache Code Review

1. **Expired entries are never removed.**
   - Issue: The cache checks TTL but does not remove expired entries from the map.
   - Potential Impact: Over time this leads to unbounded memory growth and increased GC pressure, with a real risk of running out of heap.

2. **Expiration is only checked on reads.**
   - Issue: Entries are only evaluated for expiration when `get()` is called.
   - Potential Impact: Expired entries that are never read remain in memory, so `size()` does not reflect the actual usable cache contents.

3. **Wall-clock time is used for expiration.**
   - Issue: `System.currentTimeMillis()` is affected by clock adjustments.
   - Potential Impact: Time shifts can cause entries to expire too early or stay valid longer than intended, resulting in incorrect cache behavior.

4. **No size limit or eviction policy.**
   - Issue: The cache can grow without bounds.
   - Potential Impact: With a large or unpredictable key space, this can exhaust memory and impact application stability.

5. **Cache stampede on expiration.**
   - Issue: When an entry expires, all concurrent callers see a miss and may recompute the value at the same time.
   - Potential Impact: This can overload downstream systems and increase latency under load.

6. **Expired entries remain in the map.**
   - Issue: Even after expiration, entries are repeatedly checked but never removed.
   - Potential Impact: This wastes CPU and memory and reduces the effectiveness of the cache over time.

7. **`size()` is not a reliable metric.**
   - Issue: `ConcurrentHashMap.size()` is approximate under concurrency and includes expired entries.
   - Potential Impact: Using it for monitoring or limits can be misleading.

8. **TTL is hard-coded.**
   - Issue: The TTL value is fixed in the class.
   - Potential Impact: This makes the cache harder to tune and adapt to different use cases or environments.

9. **`null` return value is ambiguous.**
   - Issue: `get()` returns `null` for both missing and expired entries.
   - Potential Impact: Callers cannot tell whether a value was never cached or has just expired.

10. **No visibility into cache behavior.**
    - Issue: There are no counters or metrics for hits, misses, or expirations.
    - Potential Impact: This makes it difficult to understand how the cache behaves in production or to debug issues.