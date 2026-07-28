package com.malhaebom.malhaebom.presentation.cookie;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(RefreshTokenCookieProperties.class)
public class RefreshCookieProvider {

	public static final String REFRESH_TOKEN_KEY = "refresh_token";

	private final RefreshTokenCookieProperties properties;
	private final CookieProvider cookieProvider;

	public ResponseCookie create(String refreshToken) {
		return cookieProvider.create(
			REFRESH_TOKEN_KEY,
			refreshToken,
			properties,
			properties.getTtl()
		);
	}

	public ResponseCookie expire() {
		return cookieProvider.create(
			REFRESH_TOKEN_KEY,
			"",
			properties,
			Duration.ZERO
		);
	}
}
