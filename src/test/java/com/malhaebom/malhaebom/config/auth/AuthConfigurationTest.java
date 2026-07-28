package com.malhaebom.malhaebom.config.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthConfigurationTest {

	private static final String VALID_SECRET_BASE64 =
		"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Test
	void 정상_설정을_AuthProperties에_바인딩한다() {
		validContextRunner().run(context -> {
			assertThat(context).hasNotFailed();

			AuthProperties properties = context.getBean(AuthProperties.class);
			assertThat(properties.issuer()).isEqualTo("malhaebom-backend");
			assertThat(properties.audience()).isEqualTo("malhaebom-api");
			assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
			assertThat(properties.refreshSessionTtl()).isEqualTo(Duration.ofDays(14));
			assertThat(properties.jwt()).isNotNull();
		});
	}

	@Test
	void Refresh_Session_TTL이_Access_Token_TTL보다_길지_않으면_실패한다() {
		contextRunner("15m", "15m", VALID_SECRET_BASE64).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage(
					"malhaebom.auth.refresh-session-ttl must be longer than "
						+ "malhaebom.auth.access-token-ttl."
				);
		});
	}

	@Test
	void 비밀키가_32바이트보다_짧으면_실패한다() {
		String sixteenByteSecretBase64 = "MDEyMzQ1Njc4OWFiY2RlZg==";

		contextRunner("15m", "14d", sixteenByteSecretBase64).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage(
					"malhaebom.auth.jwt.secret-base64 must decode to at least 32 bytes."
				);
		});
	}

	@Test
	void 비밀키가_올바른_Base64가_아니면_실패한다() {
		contextRunner("15m", "14d", "%%%invalid%%%").run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
				.hasRootCauseInstanceOf(IllegalArgumentException.class)
				.hasRootCauseMessage(
					"malhaebom.auth.jwt.secret-base64 must be valid Base64."
				);
		});
	}

	@Test
	void 정상_비밀키로_HS256_SecretKey를_생성한다() {
		validContextRunner().run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(SecretKey.class);

			SecretKey secretKey = context.getBean(SecretKey.class);
			assertThat(secretKey.getAlgorithm()).isEqualTo("HmacSHA256");
			assertThat(secretKey.getEncoded()).hasSize(32);
		});
	}

	@Test
	void 기본_Clock은_UTC를_사용한다() {
		validContextRunner().run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(Clock.class);
			assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
		});
	}

	@Test
	void 테스트에서_고정_Clock으로_대체할_수_있다() {
		Clock fixedClock = Clock.fixed(
			Instant.parse("2026-01-01T00:00:00Z"),
			ZoneOffset.UTC
		);

		validContextRunner()
			.withBean(Clock.class, () -> fixedClock)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(Clock.class);
				assertThat(context.getBean(Clock.class)).isSameAs(fixedClock);
			});
	}

	private ApplicationContextRunner validContextRunner() {
		return contextRunner("15m", "14d", VALID_SECRET_BASE64);
	}

	private ApplicationContextRunner contextRunner(
		String accessTokenTtl,
		String refreshSessionTtl,
		String secretBase64
	) {
		return new ApplicationContextRunner()
			.withUserConfiguration(AuthConfiguration.class)
			.withPropertyValues(
				"malhaebom.auth.issuer=malhaebom-backend",
				"malhaebom.auth.audience=malhaebom-api",
				"malhaebom.auth.access-token-ttl=" + accessTokenTtl,
				"malhaebom.auth.refresh-session-ttl=" + refreshSessionTtl,
				"malhaebom.auth.jwt.secret-base64=" + secretBase64
			);
	}
}
