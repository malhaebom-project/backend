package com.malhaebom.malhaebom.presentation.dto;

import java.util.List;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.service.dto.AnswerFeedback;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

public record SubmitAnswerResponse(
	Long answerId,
	Long sessionQuestionId,
	int attemptNo,
	String answerText,
	AnswerResult result,
	int score,
	String modelAnswer,
	List<String> matchedKeywords,
	List<String> missingKeywords,
	String feedbackText,
	String feedbackTtsUrl,
	boolean canRetry,
	int remainingAttempts
) {
	public static SubmitAnswerResponse from(
		AnswerSubmissionResult submission,
		AnswerFeedback feedback
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
			feedback.matchedKeywords(),
			feedback.missingKeywords(),
			feedback.feedbackText(),
			null,
			submission.canRetry(),
			submission.remainingAttempts()
		);
	}
}
