package com._early4coffee.tinylink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlRequest {

    // The detailed rules (scheme, host, private addresses) live in UrlRules.
    @NotBlank(message = "Original URL is required")
    @Size(max = 2048, message = "Original URL is too long (maximum 2048 characters)")
    private String originalUrl;

    @Size(max = 64, message = "Custom alias is too long")
    private String customAlias;

    /** ISO 8601 instant, for example 2026-12-01T15:30:00Z. */
    private Instant expiresAt;
}
