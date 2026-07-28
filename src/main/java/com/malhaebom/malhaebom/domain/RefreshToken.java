package com.malhaebom.malhaebom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(UUID accountId, String tokenHash, Instant expiresAt) {
        this.accountId = java.util.Objects.requireNonNull(accountId);
        this.tokenHash = java.util.Objects.requireNonNull(tokenHash);
        this.expiresAt = java.util.Objects.requireNonNull(expiresAt);
        this.revoked = false;
        this.createdAt = Instant.now();
    }

    public UUID getAccountId() {
        return accountId;
    }

    public boolean isUsable(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }

    public void revoke() {
        this.revoked = true;
    }
}
