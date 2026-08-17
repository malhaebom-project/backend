package com.malhaebom.malhaebom.service.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record AnswerSubmissionDeadline(Instant expiresAt) {

	public AnswerSubmissionDeadline {
		Objects.requireNonNull(expiresAt, "답변 제출 작업 기한은 null일 수 없습니다.");
	}

	public static AnswerSubmissionDeadline startingAt(
		Instant startedAt,
		Duration processingTimeout
	) {
		Objects.requireNonNull(startedAt, "답변 제출 시작 시각은 null일 수 없습니다.");
		if (processingTimeout == null
			|| processingTimeout.isZero()
			|| processingTimeout.isNegative()) {
			throw new IllegalArgumentException(
				"답변 제출 처리 제한 시간은 0보다 커야 합니다."
			);
		}

		return new AnswerSubmissionDeadline(startedAt.plus(processingTimeout));
	}

	public boolean isExpiredAt(Instant instant) {
		Objects.requireNonNull(instant, "기준 시각은 null일 수 없습니다.");
		return !instant.isBefore(expiresAt);
	}

	public Duration remainingAt(Instant instant) {
		if (isExpiredAt(instant)) {
			return Duration.ZERO;
		}
		return Duration.between(instant, expiresAt);
	}
}
