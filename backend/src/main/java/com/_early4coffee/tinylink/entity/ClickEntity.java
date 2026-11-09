package com._early4coffee.tinylink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One click on a short link. Visitor IP addresses are deliberately not stored.
 */
@Entity
@Table(name = "clicks", indexes = {
        @Index(name = "idx_clicks_code_time", columnList = "short_code, clicked_at"),
        @Index(name = "idx_clicks_time", columnList = "clicked_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ClickEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", length = 64, nullable = false)
    private String shortCode;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "referrer", length = 512)
    private String referrer;

    public ClickEntity(String shortCode, Instant clickedAt, String userAgent, String referrer) {
        this.shortCode = shortCode;
        this.clickedAt = clickedAt;
        this.userAgent = userAgent;
        this.referrer = referrer;
    }
}
