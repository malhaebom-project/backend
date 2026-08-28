package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.LearningSession;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record LearningSessionResultResponse(
	Long sessionId,
	int questionCount,
	int correctCount,
	int correctRate,
	long studySeconds,
	OffsetDateTime completedAt
) {
	public static LearningSessionResultResponse from(LearningSession session) {
		int questionCount = session.getQuestionCount();
		int correctCount = session.getCorrectCount();

		return new LearningSessionResultResponse(
			session.getId(),
			questionCount,
			correctCount,
			calculateCorrectRate(correctCount, questionCount),
			session.getStudyDuration().getSeconds(),
			session.getCompletedAt().atOffset(ZoneOffset.UTC)
		);
	}

	private static int calculateCorrectRate(
		int correctCount,
		int questionCount
	) {
		if (questionCount == 0) {
			return 0;
		}

		return (int)Math.round(correctCount * 100.0 / questionCount);
	}
}
