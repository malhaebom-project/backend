package com.malhaebom.malhaebom.presentation.dto;

import java.util.List;

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
	List<String> matchedKeywords,
	List<String> missingKeywords,
	String feedbackText,
	String feedbackTtsUrl,
	boolean canRetry,
	int remainingAttempts
) {
	// TODO: 피드백 생성 기능 구현 후 키워드, 문구, TTS URL을 실제 결과로 교체한다.
	private static final String CORRECT_FEEDBACK_TEXT =
		"정확하고 또박또박 잘 말했어요!";
	private static final String INCORRECT_FEEDBACK_TEXT =
		"좋은 시도예요! 모범 답안을 참고해서 다시 말해 보세요.";

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
			List.of(),
			List.of(),
			answer.isCorrect()
				? CORRECT_FEEDBACK_TEXT
				: INCORRECT_FEEDBACK_TEXT,
			null,
			submission.canRetry(),
			submission.remainingAttempts()
		);
	}
}
