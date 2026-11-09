package com._early4coffee.tinylink.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Used when tinylink.cache.enabled=false (for example on Cloud Run, where Redis is not available). */
@Component
@ConditionalOnProperty(name = "tinylink.cache.enabled", havingValue = "false")
public class NoopUrlCache implements UrlCache {

    @Override
    public Optional<String> get(String shortCode) {
        return Optional.empty();
    }

    @Override
    public void put(String shortCode, String originalUrl, Duration ttl) {
        // intentionally empty
    }

    @Override
    public void evict(String shortCode) {
        // intentionally empty
    }
}
