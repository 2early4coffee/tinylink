package com._early4coffee.tinylink.services;


import com._early4coffee.tinylink.models.RateLimitData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j

public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${tinylink.rate-limit.requests-per-minute}")
    private int requestsPerMinute;

    @Value("${tinylink.rate-limit.requests-per-hour}")
    private int requestsPerHour;

    private final ConcurrentHashMap<String, RateLimitData> rateLimitDataConcurrentHashMap = new ConcurrentHashMap<>();

    private static final String REDIS_KEY_PREFIX = "ratelimit:";

    public boolean isAllowed(String clientIp) {
        String redisKey = REDIS_KEY_PREFIX + clientIp;

        LocalDateTime now = LocalDateTime.now();

        RateLimitData data = getRateLimitDataFromRedis(redisKey);

        if (data == null) {
            data = rateLimitDataConcurrentHashMap.computeIfAbsent(clientIp, k -> RateLimitData.builder()
                    .minuteCount(0)
                    .hourCount(0)
                    .minuteWindowStart(now)
                    .hourWindowStart(now)
                    .build());
        }

        if (isWithinMinuteWindow(data, now)) {
            if (data.getMinuteCount() >= requestsPerMinute) {
                log.warn("Minute limit exceeded for {}", clientIp);
                return false;
            }
        } else {
            data.setMinuteCount(0);
            data.setMinuteWindowStart(now);

        }

        if (isWithinHourWindow(data, now)) {
            if (data.getHourCount() >= requestsPerHour) {
                log.warn("Hour limit exceeded for {}", clientIp);
                return false;
            }
        } else {
            data.setHourCount(0);
            data.setHourWindowStart(now);

        }

        data.setMinuteCount(data.getMinuteCount() + 1);
        data.setHourCount(data.getHourCount() + 1);

        saveRateLimitDataToRedis(redisKey, data);

        return true;
    }

    private boolean isWithinHourWindow(RateLimitData data, LocalDateTime now) {
        return data.getHourWindowStart() != null && ChronoUnit.HOURS.between(
                data.getHourWindowStart(), now
        ) < 1;
    }

    private boolean isWithinMinuteWindow(RateLimitData data, LocalDateTime now) {
        return data.getMinuteWindowStart() != null && ChronoUnit.MINUTES.between(
                data.getMinuteWindowStart(), now
        ) < 1;
    }

    private void saveRateLimitDataToRedis(String redisKey, RateLimitData data) {
        try {
            redisTemplate.opsForValue().set(redisKey, data, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to save rate limit data to Redis: {}", e.getMessage());
        }
    }

    private RateLimitData getRateLimitDataFromRedis(String redisKey) {
        try {
            return (RateLimitData) redisTemplate.opsForValue().get(redisKey);
        } catch (Exception e) {
            log.error("Error retrieving rate limit data from Redis for key {}: {}", redisKey, e.getMessage());
            return null;
        }
    }

    public int getRemainingRequests(String clientIp) {
        String redisKey = REDIS_KEY_PREFIX + clientIp;
        RateLimitData data = getRateLimitDataFromRedis(redisKey);

        if (data == null) {
            return requestsPerMinute; // If no data, all requests are available
        }

        LocalDateTime now = LocalDateTime.now();

        if (!isWithinMinuteWindow(data, now)) {
            return requestsPerMinute; // Reset if outside the minute window
        }

        return Math.max(0, requestsPerMinute - data.getMinuteCount());
    }

    public long getTimeUntilReset(String clientIp) {
        String redisKey = REDIS_KEY_PREFIX + clientIp;
        RateLimitData data = getRateLimitDataFromRedis(redisKey);

        if (data == null || data.getMinuteWindowStart() == null) {
            return 0; // No data or no window start means no wait time
        }

        LocalDateTime now = LocalDateTime.now();
        if (data.getMinuteCount() >= requestsPerMinute) {
            LocalDateTime nextMinute = data.getMinuteWindowStart()
                    .plusMinutes(1);
            return ChronoUnit.SECONDS.between(now, nextMinute);
        }

        if (data.getHourCount() >= requestsPerHour) {
            LocalDateTime nextHour = data.getHourWindowStart()
                    .plusHours(1);
            return ChronoUnit.SECONDS.between(now, nextHour);
        }

        return 0;
    }
}
