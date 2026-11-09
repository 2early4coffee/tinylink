package com._early4coffee.tinylink.dto;

import java.time.Instant;

/** One recent click. Visitor IP addresses are not stored or exposed. */
public record RecentClick(Instant clickedAt, String userAgent, String referrer) {
}
