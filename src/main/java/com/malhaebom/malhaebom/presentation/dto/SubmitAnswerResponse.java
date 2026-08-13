package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Answer;
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
		Answer answer = submission.answer();

		return new SubmitAnswerResponse(
			answer.getId(),
			answer.getSessionQuestion().getId(),
			answer.getAttemptNo(),
			answer.getAnswerText(),
			answer.getResult(),
			answer.getScore(),
			answer.getModelAnswerSnapshot(),
			answer.getFeedbackText(),
			null,
			submission.canRetry(),
			submission.remainingAttempts()
		);
	}
}
