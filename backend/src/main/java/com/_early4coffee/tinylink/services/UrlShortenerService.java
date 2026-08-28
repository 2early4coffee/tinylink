package com._early4coffee.tinylink.services;


import com._early4coffee.tinylink.dto.ShortenUrlRequest;
import com._early4coffee.tinylink.models.ClickEvent;
import com._early4coffee.tinylink.models.UrlData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j

public class UrlShortenerService {
    private final RedisTemplate<String, Object> redisTemplate;

    private final Map<String, UrlData> urlMapping = new ConcurrentHashMap<>();

    private final Map<String, List<ClickEvent>> clickAnalytics = new ConcurrentHashMap<>();

    @Value("${tinylink.base-url}")
    private String baseUrl;

    @Value("${tiny.link.short-code-length}")
    private int shortCodeLength;

    @Value("${tiny.link.short-code-max-attempts}")
    private int maxGenerationAttempt;

    @Value("${tiny.link.cache-ttl-minutes}")
    private int cacheTtlMinutes;

    private static final String BASE_62_CHARS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public ShortenUrlRequest shortenUrl(ShortenUrlRequest request, String clientIp) {
        String shortCode = request.getCustomAlias();

        if (shortCode == null || shortCode.trim().isEmpty()) {
            shortCode = generateUniqueShortCode();
        } else {
            shortCode = shortCode.trim();
            if (shortCodeExists(shortCode)) {
                throw new IllegalArgumentException("Custom alias already exists" + shortCode);
            }
        }
    }

    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < maxGenerationAttempt; attempt++) {
            String code = generateRandomBase62();
            if (!shortCodeExists(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to generate unique short code");
    }
}
