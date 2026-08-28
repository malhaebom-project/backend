package com.malhaebom.malhaebom.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AnswerSubmissionDeadlineTest {
	@Test
	void 시작_시각과_처리_제한_시간으로_남은_시간과_만료를_판단한다() {
		Instant startedAt = Instant.parse("2026-08-17T00:00:00Z");
		AnswerSubmissionDeadline deadline = AnswerSubmissionDeadline.startingAt(
			startedAt,
			Duration.ofSeconds(25)
		);

		assertThat(deadline.expiresAt()).isEqualTo(startedAt.plusSeconds(25));
		assertThat(deadline.remainingAt(startedAt.plusSeconds(10)))
			.isEqualTo(Duration.ofSeconds(15));
		assertThat(deadline.isExpiredAt(startedAt.plusSeconds(24))).isFalse();
		assertThat(deadline.isExpiredAt(startedAt.plusSeconds(25))).isTrue();
		assertThat(deadline.remainingAt(startedAt.plusSeconds(26)))
			.isEqualTo(Duration.ZERO);
	}
}
