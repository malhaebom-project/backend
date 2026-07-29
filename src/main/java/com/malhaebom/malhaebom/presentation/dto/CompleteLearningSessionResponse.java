package com.malhaebom.malhaebom.presentation.dto;

import java.time.Duration;
import java.time.LocalDateTime;

import com.malhaebom.malhaebom.domain.learning.LearningSession;

public record CompleteLearningSessionResponse(
	Long sessionId,
	int questionCount,
	int correctCount,
	double correctRate,
	long studySeconds,
	LocalDateTime completedAt
) {

	public static CompleteLearningSessionResponse from(
		LearningSession session
	) {
		int questionCount = session.getQuestionCount();
		int correctCount = session.getCorrectCount();
		LocalDateTime completedAt = session.getCompletedAt();

		double correctRate = (double) correctCount / questionCount * 100;
		long studySeconds = Duration.between(
			session.getStartedAt(),
			completedAt
		).getSeconds();

		return new CompleteLearningSessionResponse(
			session.getId(),
			questionCount,
			correctCount,
			correctRate,
			studySeconds,
			completedAt
		);
	}
}
