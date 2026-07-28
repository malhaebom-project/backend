package com.malhaebom.malhaebom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(
        String name,
        boolean secure,
        String sameSite
) {
}
