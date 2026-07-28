package com.malhaebom.malhaebom.domain;

public enum OAuthProvider {
    GOOGLE;

    public static OAuthProvider fromRegistrationId(String registrationId) {
        return switch (registrationId.toLowerCase(java.util.Locale.ROOT)) {
            case "google" -> GOOGLE;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 OAuth 제공자입니다: " + registrationId
            );
        };
    }
}
