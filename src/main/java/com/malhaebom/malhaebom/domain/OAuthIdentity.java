package com.malhaebom.malhaebom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth_identities")
public class OAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected OAuthIdentity() {
    }

    public OAuthIdentity(
            Account account,
            OAuthProvider provider,
            String providerUserId
    ) {
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("providerUserId는 필수입니다.");
        }
        this.account = java.util.Objects.requireNonNull(account);
        this.provider = java.util.Objects.requireNonNull(provider);
        this.providerUserId = providerUserId;
        this.createdAt = Instant.now();
        this.lastLoginAt = this.createdAt;
    }

    public void markLogin() {
        this.lastLoginAt = Instant.now();
    }

    public Account getAccount() {
        return account;
    }
}
