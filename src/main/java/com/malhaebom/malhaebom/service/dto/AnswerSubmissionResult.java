package com.malhaebom.malhaebom.service.dto;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerAttemptPolicy;

public record AnswerSubmissionResult(
	Answer answer,
	boolean canRetry,
	int remainingAttempts
) {

	public static AnswerSubmissionResult from(Answer answer) {
		if (answer == null) {
			throw new IllegalArgumentException("답변은 null일 수 없습니다.");
		}
		return new AnswerSubmissionResult(
			answer,
			AnswerAttemptPolicy.canRetry(answer),
			AnswerAttemptPolicy.remainingAttempts(answer)
		);
	}
}
