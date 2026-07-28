package com.malhaebom.malhaebom.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import com.malhaebom.malhaebom.domain.AuthSession;

@DataJpaTest
@ActiveProfiles("test")
class AuthSessionRepositoryTest {

	private static final UUID ACCOUNT_ID =
		UUID.fromString("6dc3f2fc-0562-4f64-b112-3f959ae62ee2");
	private static final Instant CREATED_AT = Instant.parse("2026-07-28T02:00:00Z");
	private static final Duration REFRESH_SESSION_TTL = Duration.ofDays(14);
	private static final String REFRESH_TOKEN_HASH = "1".repeat(64);
	private static final String ROTATED_REFRESH_TOKEN_HASH = "2".repeat(64);

	@Autowired
	private AuthSessionRepository authSessionRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 저장한_세션을_Refresh_Token_해시로_조회한다() {
		AuthSession saved = authSessionRepository.saveAndFlush(createSession());
		entityManager.clear();

		assertThat(authSessionRepository.findByRefreshTokenHash(REFRESH_TOKEN_HASH))
			.isPresent()
			.get()
			.extracting(AuthSession::getSessionId)
			.isEqualTo(saved.getSessionId());
	}

	@Test
	void 존재하지_않는_해시는_빈_결과를_반환한다() {
		assertThat(authSessionRepository.findByRefreshTokenHash("f".repeat(64))).isEmpty();
	}

	@Test
	void 같은_Refresh_Token_해시를_중복_저장할_수_없다() {
		authSessionRepository.saveAndFlush(createSession());

		AuthSession duplicate = AuthSession.create(
			UUID.fromString("cf2a546c-f0cc-4af5-8035-a1f3781c31b1"),
			REFRESH_TOKEN_HASH,
			CREATED_AT,
			REFRESH_SESSION_TTL
		);

		assertThatThrownBy(() -> authSessionRepository.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 엔티티를_갱신하면_version이_증가한다() {
		AuthSession saved = authSessionRepository.saveAndFlush(createSession());
		long initialVersion = saved.getVersion();

		saved.rotate(ROTATED_REFRESH_TOKEN_HASH, CREATED_AT.plusSeconds(1));
		authSessionRepository.saveAndFlush(saved);
		entityManager.clear();

		AuthSession updated = authSessionRepository.findById(saved.getSessionId()).orElseThrow();
		assertThat(updated.getVersion()).isEqualTo(initialVersion + 1);
		assertThat(updated.getRefreshTokenHash()).isEqualTo(ROTATED_REFRESH_TOKEN_HASH);
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
