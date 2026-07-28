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
@Table(name = "oauth_login_codes")
public class OAuthLoginCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OAuthLoginCode() {
    }

    public OAuthLoginCode(UUID accountId, String codeHash, Instant expiresAt) {
        this.accountId = java.util.Objects.requireNonNull(accountId);
        this.codeHash = java.util.Objects.requireNonNull(codeHash);
        this.expiresAt = java.util.Objects.requireNonNull(expiresAt);
        this.createdAt = Instant.now();
    }

    public UUID consume(Instant now) {
        if (usedAt != null) {
            throw new IllegalStateException("이미 사용한 OAuth 로그인 코드입니다.");
        }
        if (!expiresAt.isAfter(now)) {
            throw new IllegalStateException("만료된 OAuth 로그인 코드입니다.");
        }
        usedAt = now;
        return accountId;
    }
}
