package com.malhaebom.malhaebom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(
        String frontendBaseUrl,
        long loginCodeSeconds
) {
    public OAuthProperties {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            throw new IllegalArgumentException("OAuth frontendBaseUrl은 필수입니다.");
        }
        if (loginCodeSeconds <= 0) {
            throw new IllegalArgumentException("OAuth 로그인 코드 만료 시간은 0보다 커야 합니다.");
        }
    }
}
