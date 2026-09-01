package com._early4coffee.tinylink.services;

import com._early4coffee.tinylink.dto.ShortenUrlRequest;
import com._early4coffee.tinylink.dto.ShortenUrlResponse;
import com._early4coffee.tinylink.dto.UrlStatsResponse;
import com._early4coffee.tinylink.models.ClickEvent;
import com._early4coffee.tinylink.models.UrlData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlShortenerService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final Map<String, UrlData> urlMappings = new ConcurrentHashMap<>();

    private final Map<String, List<ClickEvent>> clickAnalytics = new ConcurrentHashMap<>();

    @Value("${tinylink.base-url}")
    private String baseUrl;

    @Value("${tinylink.short-code.length}")
    private int shortCodeLength;

    @Value("${tinylink.short-code.max-attempts}")
    private int maxGenerationAttempt;

    @Value("${tinylink.cache.ttl-minutes}")
    private int cacheTtlMinutes;

    private static final String BASE_62_CHARS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public ShortenUrlResponse shortenUrl(ShortenUrlRequest request, String clientIp) {
        String shortCode = request.getCustomAlias();

        if (shortCode == null || shortCode.trim().isEmpty()) {
            shortCode = generateUniqueShortCode();
        } else {
            shortCode = shortCode.trim();
            if (shortCodeExists(shortCode)) {
                throw new IllegalArgumentException("Custom alias already exists: " + shortCode);
            }
        }

        UrlData urlData = UrlData.builder()
                .originalUrl(request.getOriginalUrl())
                .shortCode(shortCode)
                .createdAt(LocalDateTime.now())
                .expiresAt(request.getExpiresAt())
                .clickCount(0)
                .createdBy(clientIp)
                .isActive(true)
                .clickEvents(new ArrayList<>())
                .build();

        urlMappings.put(shortCode, urlData);
        clickAnalytics.put(shortCode, new ArrayList<>());

        cacheUrl(shortCode, request.getOriginalUrl());

        log.info("Shortened URL created: {} -> {}", shortCode, request.getOriginalUrl());

        return ShortenUrlResponse.builder()
                .shortUrl(buildShortUrl(shortCode))
                .shortCode(shortCode)
                .originalUrl(request.getOriginalUrl())
                .createdAt(urlData.getCreatedAt())
                .expiresAt(urlData.getExpiresAt())
                .build();
    }

    private String buildShortUrl(String shortCode) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/api/" + shortCode;
    }

    private void cacheUrl(String shortCode, String originalUrl) {
        try {
            redisTemplate.opsForValue().set("url" + shortCode, originalUrl, cacheTtlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to cache URL for {} : {}", shortCode, e.getMessage());
        }
    }

    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < maxGenerationAttempt; attempt++) {
            String code = generateRandomBase62();
            if (!shortCodeExists(code)) {
                return code;
            }
        }
        throw new RuntimeException("Failed to generate unique short code after " + maxGenerationAttempt + " attempts");
    }

    private boolean shortCodeExists(String code) {
        return urlMappings.containsKey(code);
    }

    private String generateRandomBase62() {
        StringBuilder sb = new StringBuilder(shortCodeLength);
        for (int i = 0; i < shortCodeLength; i++) {
            int index = ThreadLocalRandom.current().nextInt(BASE_62_CHARS.length());
            sb.append(BASE_62_CHARS.charAt(index));
        }
        return sb.toString();
    }

    public Optional<String> getOriginalUrl(String shortCode) {
        String cachedUrl = getCacheUrl(shortCode);
        if (cachedUrl != null) {
            return Optional.of(cachedUrl);
        }

        UrlData urlData = urlMappings.get(shortCode);
        if (urlData != null && urlData.isActive()) {
            if (isExpired(urlData)) {
                urlData.setActive(false);
                return Optional.empty();
            }

            cacheUrl(shortCode, urlData.getOriginalUrl());
            return Optional.of(urlData.getOriginalUrl());
        }
        return Optional.empty();
    }

    private boolean isExpired(UrlData urlData) {
        return urlData.getExpiresAt() != null && urlData.getExpiresAt().isBefore(LocalDateTime.now());
    }

    private String getCacheUrl(String shortCode) {
        try {
            return (String) redisTemplate.opsForValue().get("url" + shortCode);
        } catch (Exception e) {
            log.warn("Failed to retrieve cached URL for {} : {}", shortCode, e.getMessage());
            return null;
        }
    }

    public void recordClick(String shortCode, String clientIp, String userAgent, String referrer) {
        UrlData urlData = urlMappings.get(shortCode);
        if (urlData != null && urlData.isActive()) {
            urlData.setClickCount(urlData.getClickCount() + 1);

            ClickEvent clickEvent = ClickEvent.builder()
                    .timestamp(LocalDateTime.now())
                    .ipAddress(clientIp)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .build();
            clickAnalytics.get(shortCode).add(clickEvent);
            log.debug("Recorded click for short code: {}", shortCode);
        }
    }

    public Optional<UrlStatsResponse> getUrlStats(String shortCode) {
        UrlData urlData = urlMappings.get(shortCode);

        if(urlData == null){
            return  Optional.empty();
        }

        return Optional.of(
                UrlStatsResponse.builder()
                        .shortCode(shortCode)
                        .originalUrl(urlData.getOriginalUrl())
                        .clickCount(urlData.getClickCount())
                        .createdAt(urlData.getCreatedAt())
                        .expiresAt(urlData.getExpiresAt())
                        .isActive(urlData.isActive())
                        .createdBy(urlData.getCreatedBy())
                        .build()
        );
    }
}