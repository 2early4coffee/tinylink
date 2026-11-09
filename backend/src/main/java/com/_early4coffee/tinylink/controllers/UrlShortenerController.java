package com._early4coffee.tinylink.controllers;

import com._early4coffee.tinylink.dto.ShortenUrlRequest;
import com._early4coffee.tinylink.dto.ShortenUrlResponse;
import com._early4coffee.tinylink.dto.UrlAnalyticsResponse;
import com._early4coffee.tinylink.dto.UrlStatsResponse;
import com._early4coffee.tinylink.services.RateLimitService;
import com._early4coffee.tinylink.services.RateLimitService.Bucket;
import com._early4coffee.tinylink.services.UrlShortenerService;
import com._early4coffee.tinylink.services.UrlShortenerService.OwnerCheck;
import com._early4coffee.tinylink.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@Slf4j
@RequiredArgsConstructor
public class UrlShortenerController {

    static final String OWNER_TOKEN_HEADER = "X-Owner-Token";
    private static final int MAX_CODE_LENGTH = 64;

    private final UrlShortenerService urlShortenerService;
    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/shorten")
    public ResponseEntity<?> shortenUrl(
            @Valid @RequestBody ShortenUrlRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        RateLimitService.Result limit = rateLimitService.check(Bucket.SHORTEN, clientIp);
        if (!limit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(limit.retryAfterSeconds()))
                    .body(Map.of(
                            "error", "Rate limit exceeded",
                            "remainingRequests", limit.remaining(),
                            "timeUntilReset", limit.retryAfterSeconds()
                    ));
        }

        try {
            ShortenUrlResponse response = urlShortenerService.shortenUrl(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        // Anything else (database errors, unexpected bugs) is logged and answered
        // with a generic message by ApiExceptionHandler or Spring's default handler.
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToUrl(
            @PathVariable String shortCode,
            HttpServletRequest request
    ) {
        if (shortCode.length() > MAX_CODE_LENGTH) {
            return ResponseEntity.notFound().build();
        }

        String clientIp = clientIpResolver.resolve(request);
        if (!rateLimitService.check(Bucket.REDIRECT, clientIp).allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        Optional<String> originalUrl = urlShortenerService.getOriginalUrl(shortCode);
        if (originalUrl.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            urlShortenerService.recordClick(shortCode,
                    request.getHeader(HttpHeaders.USER_AGENT), request.getHeader(HttpHeaders.REFERER));
        } catch (Exception e) {
            // Failing to count a click must never stop the visitor from reaching the link.
            log.warn("Could not record click for {}: {}", shortCode, e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl.get())
                .build();
    }

    @GetMapping("/stats/{shortCode}")
    public ResponseEntity<?> getUrlStats(@PathVariable String shortCode) {
        if (shortCode.length() > MAX_CODE_LENGTH) {
            return notFound();
        }
        Optional<UrlStatsResponse> stats = urlShortenerService.getUrlStats(shortCode);
        return stats.<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(UrlShortenerController::notFound);
    }

    @GetMapping("/analytics/{shortCode}")
    public ResponseEntity<?> getUrlAnalytics(
            @PathVariable String shortCode,
            @RequestHeader(value = OWNER_TOKEN_HEADER, required = false) String ownerToken
    ) {
        ResponseEntity<?> denied = rejectUnlessOwner(shortCode, ownerToken);
        if (denied != null) {
            return denied;
        }
        Optional<UrlAnalyticsResponse> analytics = urlShortenerService.getUrlAnalytics(shortCode);
        return analytics.<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(UrlShortenerController::notFound);
    }

    @DeleteMapping("/{shortCode}")
    public ResponseEntity<?> deleteUrl(
            @PathVariable String shortCode,
            @RequestHeader(value = OWNER_TOKEN_HEADER, required = false) String ownerToken
    ) {
        ResponseEntity<?> denied = rejectUnlessOwner(shortCode, ownerToken);
        if (denied != null) {
            return denied;
        }
        urlShortenerService.deleteUrl(shortCode);
        return ResponseEntity.ok(Map.of("message", "URL deleted successfully"));
    }

    /** Returns an error response, or null when the caller owns the link. */
    private ResponseEntity<?> rejectUnlessOwner(String shortCode, String ownerToken) {
        if (shortCode.length() > MAX_CODE_LENGTH) {
            return notFound();
        }
        OwnerCheck check = urlShortenerService.checkOwner(shortCode, ownerToken);
        return switch (check) {
            case OK -> null;
            case NOT_FOUND -> notFound();
            case FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "A valid " + OWNER_TOKEN_HEADER + " header is required"));
        };
    }

    private static ResponseEntity<?> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Short code not found"));
    }
}
