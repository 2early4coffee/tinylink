package com._early4coffee.tinylink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * One short link. The short code is the primary key, so the database itself
 * guarantees that two links can never share a code.
 */
@Entity
@Table(name = "urls")
@Getter
@Setter
@NoArgsConstructor
public class UrlEntity implements Persistable<String> {

    @Id
    @Column(name = "short_code", length = 64, nullable = false)
    private String shortCode;

    @Column(name = "original_url", length = 2048, nullable = false)
    private String originalUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    /** SHA-256 (hex) of the owner token. The token itself is never stored. */
    @Column(name = "owner_token_hash", length = 64, nullable = false)
    private String ownerTokenHash;

    // Tells Spring Data to INSERT instead of "select, then merge", so a duplicate
    // code fails with a constraint error instead of silently overwriting a link.
    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean newEntity = true;

    public UrlEntity(String shortCode, String originalUrl, Instant createdAt,
                     Instant expiresAt, String ownerTokenHash) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.ownerTokenHash = ownerTokenHash;
        this.active = true;
        this.clickCount = 0;
    }

    @Override
    public String getId() {
        return shortCode;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }
}
