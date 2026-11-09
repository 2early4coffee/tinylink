package com._early4coffee.tinylink.services;

import com._early4coffee.tinylink.cache.UrlCache;
import com._early4coffee.tinylink.dto.RecentClick;
import com._early4coffee.tinylink.dto.ShortenUrlRequest;
import com._early4coffee.tinylink.dto.ShortenUrlResponse;
import com._early4coffee.tinylink.dto.UrlAnalyticsResponse;
import com._early4coffee.tinylink.dto.UrlStatsResponse;
import com._early4coffee.tinylink.entity.ClickEntity;
import com._early4coffee.tinylink.entity.UrlEntity;
import com._early4coffee.tinylink.repository.ClickRepository;
import com._early4coffee.tinylink.repository.UrlRepository;
import com._early4coffee.tinylink.util.UrlRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerService {

    public enum OwnerCheck { OK, NOT_FOUND, FORBIDDEN }

    private static final String BASE_62_CHARS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ANALYTICS_ROWS = 10_000;
    private static final long PURGE_EVERY_MS = 3_600_000L;

    private final UrlRepository urlRepository;
    private final ClickRepository clickRepository;
    private final UrlCache urlCache;
    private final Clock clock;

    private final AtomicLong lastPurge = new AtomicLong(0);

    @Value("${tinylink.base-url}")
    private String baseUrl;

    @Value("${tinylink.short-code.length}")
    private int shortCodeLength;

    @Value("${tinylink.short-code.max-attempts}")
    private int maxGenerationAttempts;

    @Value("${tinylink.cache.ttl-minutes}")
    private int cacheTtlMinutes;

    @Value("${tinylink.stats-timezone:UTC}")
    private String statsTimezone;

    @Value("${tinylink.analytics.retention-days:90}")
    private int retentionDays;

    // ---------------------------------------------------------------- create

    public ShortenUrlResponse shortenUrl(ShortenUrlRequest request) {
        String originalUrl = UrlRules.validateTarget(request.getOriginalUrl());
        String alias = UrlRules.validateAlias(request.getCustomAlias());

        Instant now = clock.instant();
        Instant expiresAt = request.getExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Expiry time must be in the future");
        }

        String ownerToken = generateOwnerToken();
        String tokenHash = hashToken(ownerToken);

        UrlEntity saved = null;
        if (alias != null) {
            saved = tryInsert(alias, originalUrl, now, expiresAt, tokenHash);
            if (saved == null) {
                throw new IllegalArgumentException("Custom alias already exists: " + alias);
            }
        } else {
            for (int attempt = 0; attempt < maxGenerationAttempts && saved == null; attempt++) {
                saved = tryInsert(generateRandomCode(), originalUrl, now, expiresAt, tokenHash);
            }
            if (saved == null) {
                throw new IllegalStateException(
                        "Failed to generate a unique short code after " + maxGenerationAttempts + " attempts");
            }
        }

        cacheUrl(saved);
        log.info("Short link created: {}", saved.getShortCode());

        return ShortenUrlResponse.builder()
                .shortUrl(buildShortUrl(saved.getShortCode()))
                .shortCode(saved.getShortCode())
                .originalUrl(saved.getOriginalUrl())
                .createdAt(saved.getCreatedAt())
                .expiresAt(saved.getExpiresAt())
                .ownerToken(ownerToken)
                .build();
    }

    /** Returns null if the code is already taken. The database primary key is the final judge. */
    private UrlEntity tryInsert(String code, String url, Instant now, Instant expiresAt, String tokenHash) {
        if (urlRepository.existsById(code)) {
            return null;
        }
        try {
            return urlRepository.saveAndFlush(new UrlEntity(code, url, now, expiresAt, tokenHash));
        } catch (DataIntegrityViolationException e) {
            return null;
        }
    }

    // -------------------------------------------------------------- redirect

    public Optional<String> getOriginalUrl(String shortCode) {
        Optional<String> cached = urlCache.get(shortCode);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<UrlEntity> found = urlRepository.findById(shortCode);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UrlEntity entity = found.get();
        if (!entity.isActive() || isExpired(entity)) {
            return Optional.empty();
        }

        cacheUrl(entity);
        return Optional.of(entity.getOriginalUrl());
    }

    // Not one big transaction on purpose: a failed retention cleanup must not undo a click.
    public void recordClick(String shortCode, String userAgent, String referrer) {
        if (urlRepository.incrementClicks(shortCode) == 0) {
            return;
        }
        clickRepository.save(new ClickEntity(shortCode, clock.instant(),
                truncate(userAgent, 255), truncate(referrer, 512)));
        purgeOldClicksOccasionally();
    }

    // ----------------------------------------------------------------- reads

    public Optional<UrlStatsResponse> getUrlStats(String shortCode) {
        return urlRepository.findById(shortCode).map(entity -> UrlStatsResponse.builder()
                .shortCode(entity.getShortCode())
                .originalUrl(entity.getOriginalUrl())
                .clickCount(entity.getClickCount())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .active(entity.isActive() && !isExpired(entity))
                .build());
    }

    public Optional<UrlAnalyticsResponse> getUrlAnalytics(String shortCode) {
        Optional<UrlEntity> found = urlRepository.findById(shortCode);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UrlEntity entity = found.get();

        List<RecentClick> recent = clickRepository.findTop10ByShortCodeOrderByClickedAtDesc(shortCode)
                .stream()
                .map(c -> new RecentClick(c.getClickedAt(), c.getUserAgent(), c.getReferrer()))
                .toList();

        Map<String, Long> byReferrer = new LinkedHashMap<>();
        for (Object[] row : clickRepository.countByReferrer(shortCode)) {
            byReferrer.put((String) row[0], ((Number) row[1]).longValue());
        }

        ZoneId zone = ZoneId.of(statsTimezone);
        Map<String, Long> byHour = new TreeMap<>();
        Map<String, Long> byDay = new TreeMap<>();
        for (Instant time : clickRepository.findClickTimes(shortCode, PageRequest.of(0, MAX_ANALYTICS_ROWS))) {
            ZonedDateTime local = time.atZone(zone);
            byHour.merge(String.format("%02d:00", local.getHour()), 1L, Long::sum);
            byDay.merge(local.toLocalDate().toString(), 1L, Long::sum);
        }

        return Optional.of(UrlAnalyticsResponse.builder()
                .shortCode(shortCode)
                .originalUrl(entity.getOriginalUrl())
                .totalClicks(entity.getClickCount())
                .createdAt(entity.getCreatedAt())
                .recentClicks(recent)
                .clicksByReferrer(byReferrer)
                .clicksByHour(byHour)
                .clicksByDay(byDay)
                .build());
    }

    // ----------------------------------------------------------------- owner

    public OwnerCheck checkOwner(String shortCode, String presentedToken) {
        Optional<UrlEntity> found = urlRepository.findById(shortCode);
        if (found.isEmpty()) {
            return OwnerCheck.NOT_FOUND;
        }
        if (presentedToken == null || presentedToken.isBlank() || presentedToken.length() > 128) {
            return OwnerCheck.FORBIDDEN;
        }
        byte[] presented = hashToken(presentedToken.trim()).getBytes(StandardCharsets.UTF_8);
        byte[] stored = found.get().getOwnerTokenHash().getBytes(StandardCharsets.UTF_8);
        // Constant-time comparison, so response timing does not leak the hash.
        return MessageDigest.isEqual(presented, stored) ? OwnerCheck.OK : OwnerCheck.FORBIDDEN;
    }

    /** Removes the link and its click history, which also frees the alias. */
    @Transactional
    public void deleteUrl(String shortCode) {
        clickRepository.deleteAllByCode(shortCode);
        urlRepository.deleteById(shortCode);
        urlCache.evict(shortCode);
        log.info("Short link deleted: {}", shortCode);
    }

    // --------------------------------------------------------------- helpers

    private boolean isExpired(UrlEntity entity) {
        return entity.getExpiresAt() != null && !entity.getExpiresAt().isAfter(clock.instant());
    }

    /** The cache entry never outlives the link itself, so expired links stop redirecting on time. */
    private void cacheUrl(UrlEntity entity) {
        Duration ttl = Duration.ofMinutes(cacheTtlMinutes);
        if (entity.getExpiresAt() != null) {
            Duration untilExpiry = Duration.between(clock.instant(), entity.getExpiresAt());
            if (untilExpiry.compareTo(ttl) < 0) {
                ttl = untilExpiry;
            }
        }
        if (ttl.toMillis() < 1000) {
            return;
        }
        urlCache.put(entity.getShortCode(), entity.getOriginalUrl(), ttl);
    }

    private void purgeOldClicksOccasionally() {
        long now = clock.millis();
        long last = lastPurge.get();
        if (now - last < PURGE_EVERY_MS || !lastPurge.compareAndSet(last, now)) {
            return;
        }
        try {
            int removed = clickRepository.deleteOlderThan(clock.instant().minus(retentionDays, ChronoUnit.DAYS));
            if (removed > 0) {
                log.info("Removed {} click records older than {} days", removed, retentionDays);
            }
        } catch (Exception e) {
            log.warn("Click retention cleanup failed: {}", e.getMessage());
        }
    }

    private String buildShortUrl(String shortCode) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/api/" + shortCode;
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(shortCodeLength);
        for (int i = 0; i < shortCodeLength; i++) {
            sb.append(BASE_62_CHARS.charAt(RANDOM.nextInt(BASE_62_CHARS.length())));
        }
        return sb.toString();
    }

    private static String generateOwnerToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
