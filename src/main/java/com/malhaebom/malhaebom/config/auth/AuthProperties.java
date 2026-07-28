package com.malhaebom.malhaebom.config.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code malhaebom.auth} 하위의 인증 설정을 타입 안전하게 바인딩한다.
 *
 * <p>애플리케이션이 잘못된 인증 설정으로 실행되지 않도록 생성 시점에 필수 값과
 * 만료시간 간의 관계를 검증한다.</p>
 *
 * @param issuer Access Token의 발급자(issuer)
 * @param audience Access Token의 대상(audience)
 * @param accessTokenTtl Access Token 유효기간
 * @param refreshSessionTtl Refresh Session 유효기간
 * @param jwt JWT 서명 관련 설정
 */
@ConfigurationProperties(prefix = "malhaebom.auth")
public record AuthProperties(
	String issuer,
	String audience,
	Duration accessTokenTtl,
	Duration refreshSessionTtl,
	Jwt jwt
) {

	public AuthProperties {
		requireText(issuer, "malhaebom.auth.issuer");
		requireText(audience, "malhaebom.auth.audience");
		requirePositive(accessTokenTtl, "malhaebom.auth.access-token-ttl");
		requirePositive(refreshSessionTtl, "malhaebom.auth.refresh-session-ttl");

		if (refreshSessionTtl.compareTo(accessTokenTtl) <= 0) {
			throw new IllegalArgumentException(
				"malhaebom.auth.refresh-session-ttl must be longer than malhaebom.auth.access-token-ttl."
			);
		}
		if (jwt == null) {
			throw new IllegalArgumentException("malhaebom.auth.jwt is required.");
		}
	}

	private static void requireText(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(propertyName + " must not be blank.");
		}
	}

	private static void requirePositive(Duration value, String propertyName) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(propertyName + " must be positive.");
		}
	}

	/**
	 * JWT 서명에 필요한 비밀키 설정이다.
	 *
	 * @param secretBase64 Base64로 인코딩된 비밀키. 실제 바이트 길이는
	 *                     {@link AuthConfiguration}에서 검증한다.
	 */
	public record Jwt(String secretBase64) {

		public Jwt {
			requireText(secretBase64, "malhaebom.auth.jwt.secret-base64");
		}
	}
}
