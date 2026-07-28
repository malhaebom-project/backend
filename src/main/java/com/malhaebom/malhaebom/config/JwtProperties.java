package com.malhaebom.malhaebom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTokenSeconds,
        long refreshTokenSeconds
) {
    public JwtProperties {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret은 32바이트 이상이어야 합니다.");
        }
        if (accessTokenSeconds <= 0 || refreshTokenSeconds <= 0) {
            throw new IllegalArgumentException("JWT 만료 시간은 0보다 커야 합니다.");
        }
    }
}
