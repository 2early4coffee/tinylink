package com._early4coffee.tinylink.cache;

import java.time.Duration;
import java.util.Optional;

/** Optional cache for redirect lookups. A cache failure must never break a redirect. */
public interface UrlCache {

    Optional<String> get(String shortCode);

    void put(String shortCode, String originalUrl, Duration ttl);

    void evict(String shortCode);
}
