package com._early4coffee.tinylink.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP fixed-window rate limiter, held in memory.
 *
 * This is correct as long as one instance serves traffic, which is how the Cloud Run
 * deploy is configured (max-instances 1). Each check is atomic per key, and the map
 * is size-capped so it cannot grow without limit.
 */
@Service
public class RateLimitService {

    public enum Bucket { SHORTEN, REDIRECT }

    public record Result(boolean allowed, int remaining, long retryAfterSeconds) {
    }

    private static final class Window {
        long minuteStart;
        int minuteCount;
        long hourStart;
        int hourCount;

        Window(long now) {
            this.minuteStart = now;
            this.hourStart = now;
        }
    }

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 3_600_000L;
    private static final int SOFT_CAP = 10_000;
    private static final int HARD_CAP = 50_000;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int shortenPerMinute;
    private final int shortenPerHour;
    private final int redirectPerMinute;

    public RateLimitService(
            Clock clock,
            @Value("${tinylink.rate-limit.requests-per-minute}") int shortenPerMinute,
            @Value("${tinylink.rate-limit.requests-per-hour}") int shortenPerHour,
            @Value("${tinylink.rate-limit.redirects-per-minute:120}") int redirectPerMinute) {
        this.clock = clock;
        this.shortenPerMinute = shortenPerMinute;
        this.shortenPerHour = shortenPerHour;
        this.redirectPerMinute = redirectPerMinute;
    }

    public Result check(Bucket bucket, String clientIp) {
        long now = clock.millis();
        int perMinute = bucket == Bucket.SHORTEN ? shortenPerMinute : redirectPerMinute;
        int perHour = bucket == Bucket.SHORTEN ? shortenPerHour : Integer.MAX_VALUE;
        Result[] out = new Result[1];

        windows.compute(bucket.name() + ":" + clientIp, (key, existing) -> {
            Window w = existing == null ? new Window(now) : existing;

            if (now - w.minuteStart >= MINUTE_MS) {
                w.minuteStart = now;
                w.minuteCount = 0;
            }
            if (now - w.hourStart >= HOUR_MS) {
                w.hourStart = now;
                w.hourCount = 0;
            }

            if (w.minuteCount >= perMinute) {
                out[0] = new Result(false, 0, secondsUntil(w.minuteStart + MINUTE_MS, now));
                return w;
            }
            if (w.hourCount >= perHour) {
                out[0] = new Result(false, 0, secondsUntil(w.hourStart + HOUR_MS, now));
                return w;
            }

            w.minuteCount++;
            w.hourCount++;
            int remaining = Math.min(perMinute - w.minuteCount,
                    perHour == Integer.MAX_VALUE ? Integer.MAX_VALUE : perHour - w.hourCount);
            out[0] = new Result(true, Math.max(0, remaining), 0);
            return w;
        });

        trimIfLarge(now);
        return out[0];
    }

    int trackedClients() {
        return windows.size();
    }

    private void trimIfLarge(long now) {
        int size = windows.size();
        if (size <= SOFT_CAP) {
            return;
        }
        windows.entrySet().removeIf(e -> now - e.getValue().hourStart >= HOUR_MS);
        if (windows.size() > HARD_CAP) {
            windows.clear();
        }
    }

    private static long secondsUntil(long end, long now) {
        return Math.max(1, (end - now + 999) / 1000);
    }
}
