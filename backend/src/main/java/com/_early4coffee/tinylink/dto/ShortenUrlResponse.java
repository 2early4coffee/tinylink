package com._early4coffee.tinylink.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenUrlResponse {

    private String shortUrl;
    private String shortCode;
    private String originalUrl;
    private Instant createdAt;
    private Instant expiresAt;

    /** Shown once, at creation. Needed to read analytics or delete the link. */
    private String ownerToken;
}
