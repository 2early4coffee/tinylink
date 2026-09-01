package com._early4coffee.tinylink.controllers;


import com._early4coffee.tinylink.dto.ShortenUrlRequest;
import com._early4coffee.tinylink.dto.ShortenUrlResponse;
import com._early4coffee.tinylink.dto.UrlStatsResponse;
import com._early4coffee.tinylink.services.RateLimitService;
import com._early4coffee.tinylink.services.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final UrlShortenerService urlShortenerService;
    private final RateLimitService rateLimitService;

    @PostMapping("/shorten")
    public ResponseEntity<?> shortenUrl(
            @Valid @RequestBody ShortenUrlRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        if (!rateLimitService.isAllowed(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", "Rate limit exceeded",
                            "remainingRequests", rateLimitService.getRemainingRequests(clientIp),
                            "timeUntilReset", rateLimitService.getTimeUntilReset(clientIp)
                    ));
        }

        try {
            ShortenUrlResponse response = urlShortenerService.shortenUrl(request, clientIp);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error "));
        }

    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirectToUrl(
            @PathVariable String shortCode,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referrer = request.getHeader("Referer");

        Optional<String> originalUrl = urlShortenerService.getOriginalUrl(shortCode);

        if (originalUrl.isPresent()) {
            urlShortenerService.recordClick(shortCode, clientIp, userAgent, referrer);

            response.setHeader("Location", originalUrl.get());
            return ResponseEntity.status(HttpStatus.FOUND).build();
        } else {
            return ResponseEntity.notFound().build();
        }

    }

    @GetMapping("/stats/{shortCode}")
    public ResponseEntity<?> getUrlStats (@PathVariable String shortCode){
        Optional<UrlStatsResponse> urlStats = urlShortenerService.getUrlStats(shortCode);

      if ( urlStats.isPresent()) {
          return ResponseEntity.ok(urlStats.get());
      } else {
          return ResponseEntity.status(HttpStatus.NOT_FOUND)
                  .body(Map.of("error", "Short code not found"));
      }
    }

    private String getClientIp(HttpServletRequest httpRequest) {
        String xForwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = httpRequest.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return httpRequest.getRemoteAddr();
    }
}
