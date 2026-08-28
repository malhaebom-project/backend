package com.malhaebom.malhaebom.service.dto;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerAttemptPolicy;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;

public record AnswerSubmissionResult(
	Long answerId,
	Long sessionQuestionId,
	int attemptNo,
	String answerText,
	AnswerResult result,
	int score,
	String modelAnswer,
	String feedbackText,
	boolean canRetry,
	int remainingAttempts
) {
	public static AnswerSubmissionResult from(Answer answer) {
		if (answer == null) {
			throw new IllegalArgumentException("답변은 null일 수 없습니다.");
		}
		return new AnswerSubmissionResult(
			answer.getId(),
			answer.getSessionQuestion().getId(),
			answer.getAttemptNo(),
			answer.getAnswerText(),
			answer.getResult(),
			answer.getScore(),
			answer.getModelAnswerSnapshot(),
			answer.getFeedbackText(),
			AnswerAttemptPolicy.canRetry(answer),
			AnswerAttemptPolicy.remainingAttempts(answer)
		);
	}
}
