package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.service.model.AnswerSubmissionResult;

public record SubmitAnswerResponse(
	Long answerId,
	Long sessionQuestionId,
	int attemptNo,
	String answerText,
	AnswerResult result,
	int score,
	String modelAnswer,
	boolean canRetry,
	int remainingAttempts
) {

	public static SubmitAnswerResponse from(
		AnswerSubmissionResult submission
	) {
		Answer answer = submission.answer();

		return new SubmitAnswerResponse(
			answer.getId(),
			answer.getSessionQuestion().getId(),
			answer.getAttemptNo(),
			answer.getAnswerText(),
			answer.getResult(),
			answer.getScore(),
			answer.getModelAnswerSnapshot(),
			submission.canRetry(),
			submission.remainingAttempts()
		);
	}
}
