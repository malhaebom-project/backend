package com.malhaebom.malhaebom.config.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

	private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
	private static final Duration REFRESH_SESSION_TTL = Duration.ofDays(14);
	private static final AuthProperties.Jwt JWT = new AuthProperties.Jwt("test-secret");

	@Test
	void issuer와_audience는_필수이다() {
		assertThatThrownBy(() -> new AuthProperties(
			null,
			"malhaebom-api",
			ACCESS_TOKEN_TTL,
			REFRESH_SESSION_TTL,
			JWT
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("malhaebom.auth.issuer must not be blank.");

		assertThatThrownBy(() -> new AuthProperties(
			"malhaebom-backend",
			" ",
			ACCESS_TOKEN_TTL,
			REFRESH_SESSION_TTL,
			JWT
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("malhaebom.auth.audience must not be blank.");
	}

	@Test
	void 토큰과_세션의_만료시간은_양수여야_한다() {
		assertThatThrownBy(() -> new AuthProperties(
			"malhaebom-backend",
			"malhaebom-api",
			Duration.ZERO,
			REFRESH_SESSION_TTL,
			JWT
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("malhaebom.auth.access-token-ttl must be positive.");

		assertThatThrownBy(() -> new AuthProperties(
			"malhaebom-backend",
			"malhaebom-api",
			ACCESS_TOKEN_TTL,
			Duration.ofDays(-1),
			JWT
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("malhaebom.auth.refresh-session-ttl must be positive.");
	}

	@Test
	void jwt와_secretBase64는_필수이다() {
		assertThatThrownBy(() -> new AuthProperties(
			"malhaebom-backend",
			"malhaebom-api",
			ACCESS_TOKEN_TTL,
			REFRESH_SESSION_TTL,
			null
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("malhaebom.auth.jwt is required.");

		assertThatThrownBy(() -> new AuthProperties.Jwt(""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("malhaebom.auth.jwt.secret-base64 must not be blank.");
	}
}
