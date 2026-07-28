package com.malhaebom.malhaebom.presentation.dto;

public record OAuthCodeExchangeResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        AccountResponse account
) {
}
