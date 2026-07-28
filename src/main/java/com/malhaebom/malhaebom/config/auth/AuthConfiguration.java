package com.malhaebom.malhaebom.config.auth;

import java.time.Clock;
import java.util.Base64;

import javax.crypto.SecretKey;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * 인증 컴포넌트가 공통으로 사용하는 시간 기준과 JWT 서명키를 제공한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

	private static final int MINIMUM_SECRET_KEY_BYTES = 32;

	/**
	 * 인증 만료시간 계산에 사용할 UTC Clock을 제공한다.
	 *
	 * <p>기본적으로 UTC 시스템 시계를 제공한다. 테스트에서 고정 Clock Bean을
	 * 제공하면 기본 Bean 대신 해당 시계를 사용한다.</p>
	 *
	 * @return 시스템 UTC Clock
	 */
	@Bean
	@ConditionalOnMissingBean(Clock.class)
	Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * 환경설정의 Base64 비밀키를 HS256에 사용할 수 있는 {@link SecretKey}로 변환한다.
	 *
	 * @param properties 검증된 인증 설정
	 * @return 최소 256비트 이상의 HMAC 서명키
	 * @throws IllegalArgumentException 비밀키가 올바른 Base64가 아니거나 32바이트보다 짧은 경우
	 */
	@Bean
	SecretKey jwtSigningKey(AuthProperties properties) {
		String secretBase64 = properties.jwt().secretBase64();
		byte[] keyBytes;
		try {
			// JJWT 디코더보다 먼저 엄격하게 검사해 잘못된 문자가 조용히 무시되지 않게 한다.
			Base64.getDecoder().decode(secretBase64);
			keyBytes = Decoders.BASE64.decode(secretBase64);
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException(
				"malhaebom.auth.jwt.secret-base64 must be valid Base64."
			);
		}

		if (keyBytes.length < MINIMUM_SECRET_KEY_BYTES) {
			throw new IllegalArgumentException(
				"malhaebom.auth.jwt.secret-base64 must decode to at least 32 bytes."
			);
		}

		return Keys.hmacShaKeyFor(keyBytes);
	}
}
