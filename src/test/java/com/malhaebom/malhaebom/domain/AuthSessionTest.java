package com.malhaebom.malhaebom.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AuthSessionTest {

	private static final UUID ACCOUNT_ID =
		UUID.fromString("c49b1ce0-f6d9-4f3c-acb0-07f93b34b328");
	private static final Instant CREATED_AT = Instant.parse("2026-07-28T01:00:00Z");
	private static final Duration REFRESH_SESSION_TTL = Duration.ofDays(14);
	private static final String REFRESH_TOKEN_HASH = "a".repeat(64);
	private static final String ROTATED_REFRESH_TOKEN_HASH = "b".repeat(64);

	@Test
	void 생성하면_UUID와_14일_절대_만료_시각을_설정한다() {
		AuthSession session = createSession();

		assertThat(session.getSessionId()).isNotNull();
		assertThat(session.getAccountId()).isEqualTo(ACCOUNT_ID);
		assertThat(session.getRefreshTokenHash()).isEqualTo(REFRESH_TOKEN_HASH);
		assertThat(session.getCreatedAt()).isEqualTo(CREATED_AT);
		assertThat(session.getExpiresAt()).isEqualTo(CREATED_AT.plus(Duration.ofDays(14)));
		assertThat(session.getRevokedAt()).isNull();
	}

	@Test
	void 만료_전이고_폐기되지_않은_세션은_활성이다() {
		AuthSession session = createSession();

		assertThat(session.isActive(session.getExpiresAt().minusNanos(1))).isTrue();
	}

	@Test
	void 만료_시각부터_세션은_비활성이다() {
		AuthSession session = createSession();

		assertThat(session.isActive(session.getExpiresAt())).isFalse();
		assertThat(session.isActive(session.getExpiresAt().plusNanos(1))).isFalse();
	}

	@Test
	void 폐기된_세션은_만료_전이어도_비활성이다() {
		AuthSession session = createSession();
		session.revoke(CREATED_AT.plusSeconds(1));

		assertThat(session.isActive(CREATED_AT.plusSeconds(2))).isFalse();
	}

	@Test
	void 활성_세션을_회전하면_해시만_교체한다() {
		AuthSession session = createSession();
		UUID sessionId = session.getSessionId();
		Instant expiresAt = session.getExpiresAt();

		session.rotate(ROTATED_REFRESH_TOKEN_HASH, CREATED_AT.plusSeconds(1));

		assertThat(session.getRefreshTokenHash()).isEqualTo(ROTATED_REFRESH_TOKEN_HASH);
		assertThat(session.getSessionId()).isEqualTo(sessionId);
		assertThat(session.getAccountId()).isEqualTo(ACCOUNT_ID);
		assertThat(session.getCreatedAt()).isEqualTo(CREATED_AT);
		assertThat(session.getExpiresAt()).isEqualTo(expiresAt);
	}

	@Test
	void 폐기되었거나_만료된_세션은_회전할_수_없다() {
		AuthSession revokedSession = createSession();
		revokedSession.revoke(CREATED_AT.plusSeconds(1));

		assertThatThrownBy(() ->
			revokedSession.rotate(ROTATED_REFRESH_TOKEN_HASH, CREATED_AT.plusSeconds(2))
		)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Revoked or expired auth session cannot rotate its refresh token.");

		AuthSession expiredSession = createSession();

		assertThatThrownBy(() ->
			expiredSession.rotate(ROTATED_REFRESH_TOKEN_HASH, expiredSession.getExpiresAt())
		)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Revoked or expired auth session cannot rotate its refresh token.");
	}

	@Test
	void 다시_폐기해도_최초_폐기_시각을_유지한다() {
		AuthSession session = createSession();
		Instant firstRevokedAt = CREATED_AT.plusSeconds(1);

		session.revoke(firstRevokedAt);
		session.revoke(firstRevokedAt.plusSeconds(10));

		assertThat(session.getRevokedAt()).isEqualTo(firstRevokedAt);
	}

	@Test
	void 필수값과_해시_형식과_TTL을_검증한다() {
		assertThatThrownBy(() ->
			AuthSession.create(null, REFRESH_TOKEN_HASH, CREATED_AT, REFRESH_SESSION_TTL)
		)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("accountId must not be null.");

		assertThatThrownBy(() ->
			AuthSession.create(ACCOUNT_ID, null, CREATED_AT, REFRESH_SESSION_TTL)
		)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("refreshTokenHash must not be null.");

		assertThatThrownBy(() ->
			AuthSession.create(ACCOUNT_ID, "A".repeat(64), CREATED_AT, REFRESH_SESSION_TTL)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
				"refreshTokenHash must be a 64-character lowercase SHA-256 hex value."
			);

		assertThatThrownBy(() ->
			AuthSession.create(ACCOUNT_ID, REFRESH_TOKEN_HASH, null, REFRESH_SESSION_TTL)
		)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("now must not be null.");

		assertThatThrownBy(() ->
			AuthSession.create(ACCOUNT_ID, REFRESH_TOKEN_HASH, CREATED_AT, null)
		)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("refreshSessionTtl must not be null.");

		assertThatThrownBy(() ->
			AuthSession.create(ACCOUNT_ID, REFRESH_TOKEN_HASH, CREATED_AT, Duration.ZERO)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("refreshSessionTtl must be positive.");

		assertThatThrownBy(() ->
			AuthSession.create(
				ACCOUNT_ID,
				REFRESH_TOKEN_HASH,
				CREATED_AT,
				Duration.ofSeconds(-1)
			)
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("refreshSessionTtl must be positive.");
	}

	private AuthSession createSession() {
		return AuthSession.create(
			ACCOUNT_ID,
			REFRESH_TOKEN_HASH,
			CREATED_AT,
			REFRESH_SESSION_TTL
		);
	}
}
