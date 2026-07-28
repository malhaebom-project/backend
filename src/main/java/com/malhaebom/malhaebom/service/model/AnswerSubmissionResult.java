package com.malhaebom.malhaebom.service.model;

import com.malhaebom.malhaebom.domain.learning.Answer;

public record AnswerSubmissionResult(
	Answer answer,
	boolean canRetry,
	int remainingAttempts
) {
}
