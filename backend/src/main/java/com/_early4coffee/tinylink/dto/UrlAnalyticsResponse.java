package com._early4coffee.tinylink.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlAnalyticsResponse {
    private String shortCode;
    private String originalUrl;
    private long totalClicks;
    private Instant createdAt;
    private List<RecentClick> recentClicks;
    private Map<String, Long> clicksByReferrer;
    private Map<String, Long> clicksByHour;
    private Map<String, Long> clicksByDay;
}
