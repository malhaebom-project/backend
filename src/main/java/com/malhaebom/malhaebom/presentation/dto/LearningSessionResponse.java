package com.malhaebom.malhaebom.presentation.dto;

import java.time.LocalDateTime;

import com.malhaebom.malhaebom.domain.LearningSession;
import com.malhaebom.malhaebom.domain.LearningSessionStatus;

public record LearningSessionResponse(
	Long sessionId,
	LearningSessionStatus status,
	int currentQuestionIndex,
	int questionCount,
	int correctCount,
	LocalDateTime startedAt
) {

	public static LearningSessionResponse from(LearningSession session) {
		return new LearningSessionResponse(
			session.getId(),
			session.getStatus(),
			session.getCurrentQuestionIndex(),
			session.getQuestionCount(),
			session.getCorrectCount(),
			session.getStartedAt()
		);
	}
}
