package com._early4coffee.tinylink.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "tinylink.cache.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RedisUrlCache implements UrlCache {

    private static final String PREFIX = "url:";

    private final StringRedisTemplate redis;

    @Override
    public Optional<String> get(String shortCode) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + shortCode));
        } catch (Exception e) {
            log.warn("Cache read failed for {}: {}", shortCode, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String shortCode, String originalUrl, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + shortCode, originalUrl, ttl);
        } catch (Exception e) {
            log.warn("Cache write failed for {}: {}", shortCode, e.getMessage());
        }
    }

    @Override
    public void evict(String shortCode) {
        try {
            redis.delete(PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Cache delete failed for {}: {}", shortCode, e.getMessage());
        }
    }
}
