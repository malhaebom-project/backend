package com.malhaebom.malhaebom.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class LoginSessionTest {

	@Test
	void 로그인_세션을_생성하고_토큰을_회전한다() {
		User user = User.create(
			"사용자",
			"user@example.com",
			"encoded-password"
		);
		Instant createdAt = Instant.parse("2026-07-28T00:00:00Z");
		LoginSession session = LoginSession.create(
			user,
			"access-token",
			"refresh-token",
			createdAt.plusSeconds(3600),
			createdAt.plusSeconds(1209600),
			createdAt
		);

		session.rotate(
			"new-access-token",
			"new-refresh-token",
			createdAt.plusSeconds(7200),
			createdAt.plusSeconds(1213200)
		);

		assertEquals(user, session.getUser());
		assertEquals("new-access-token", session.getAccessToken());
		assertEquals("new-refresh-token", session.getRefreshToken());
		assertEquals(createdAt, session.getCreatedAt());
	}
}
