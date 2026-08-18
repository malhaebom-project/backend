package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

public record SubmitAnswerResponse(
	Long answerId,
	Long sessionQuestionId,
	int attemptNo,
	String answerText,
	AnswerResult result,
	int score,
	String modelAnswer,
	String feedbackText,
	String feedbackTtsUrl,
	boolean canRetry,
	int remainingAttempts
) {
	public static SubmitAnswerResponse from(AnswerSubmissionResult submission) {
		return new SubmitAnswerResponse(
			submission.answerId(),
			submission.sessionQuestionId(),
			submission.attemptNo(),
			submission.answerText(),
			submission.result(),
			submission.score(),
			submission.modelAnswer(),
			submission.feedbackText(),
			null,
			submission.canRetry(),
			submission.remainingAttempts()
		);
	}
}
