package com.malhaebom.malhaebom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    private Account(String email, String passwordHash, String name) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.name = requireName(name);
        this.role = AccountRole.GUARDIAN;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Account createEmailGuardian(
            String email,
            String passwordHash,
            String name
    ) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash는 필수입니다.");
        }
        return new Account(email, passwordHash, name);
    }

    public static Account createOAuthGuardian(String email, String name) {
        return new Account(email, null, name);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email은 필수입니다.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name은 필수입니다.");
        }
        return name.trim();
    }

    public boolean canLogin() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public AccountRole getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
