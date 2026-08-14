package com.malhaebom.malhaebom.domain.learning;

import java.util.Objects;

public final class AnswerAttemptPolicy {

	private static final int MAX_ATTEMPT_COUNT = 2;

	private AnswerAttemptPolicy() {
	}

	public static boolean isAllowed(int attemptNo) {
		return attemptNo >= 1 && attemptNo <= MAX_ATTEMPT_COUNT;
	}

	public static boolean canRetry(Answer answer) {
		Objects.requireNonNull(answer, "답변은 null일 수 없습니다.");
		return !answer.isCorrect()
			&& answer.getAttemptNo() < MAX_ATTEMPT_COUNT;
	}

	public static int remainingAttempts(Answer answer) {
		return canRetry(answer)
			? MAX_ATTEMPT_COUNT - answer.getAttemptNo()
			: 0;
	}
}
