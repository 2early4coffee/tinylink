package com._early4coffee.tinylink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class ShortenUrlRequest {
    @NotBlank(message = "Original URL is required")
    @Pattern(regexp = "^(http|https)://.*$", message = "Original URL must be a valid HTTP or HTTPS URL")
    private String originalUrl;
    
    private String customAlias;

    private LocalDateTime expiresAt;


}
