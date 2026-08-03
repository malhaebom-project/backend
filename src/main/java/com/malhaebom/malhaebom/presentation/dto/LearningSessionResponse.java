package com.malhaebom.malhaebom.presentation.dto;

import java.time.Duration;
import java.time.LocalDateTime;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionStatus;

public record LearningSessionResponse(
	Long sessionId,
	LearningSessionStatus status,
	int currentQuestionIndex,
	int questionCount,
	int correctCount,
	int correctRate,
	long studySeconds,
	LocalDateTime startedAt,
	LocalDateTime completedAt
) {

	public static LearningSessionResponse from(LearningSession session) {
		int questionCount = session.getQuestionCount();
		int correctCount = session.getCorrectCount();

		return new LearningSessionResponse(
			session.getId(),
			session.getStatus(),
			session.getCurrentQuestionIndex(),
			questionCount,
			correctCount,
			calculateCorrectRate(correctCount, questionCount),
			calculateStudySeconds(
				session.getStartedAt(),
				session.getCompletedAt()
			),
			session.getStartedAt(),
			session.getCompletedAt()
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

	private static long calculateStudySeconds(
		LocalDateTime startedAt,
		LocalDateTime completedAt
	) {
		LocalDateTime endAt = completedAt == null
			? LocalDateTime.now()
			: completedAt;

		return Math.max(
			0L,
			Duration.between(startedAt, endAt).getSeconds()
		);
	}
}
