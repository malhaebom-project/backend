package com.malhaebom.malhaebom.presentation.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionStatus;

public record LearningSessionResponse(
	Long sessionId,
	LearningSessionStatus status,
	int currentQuestionIndex,
	int questionCount,
	int correctCount,
	OffsetDateTime startedAt
) {
	public static LearningSessionResponse from(LearningSession session) {
		return new LearningSessionResponse(
			session.getId(),
			session.getStatus(),
			session.getCurrentQuestionIndex(),
			session.getQuestionCount(),
			session.getCorrectCount(),
			session.getStartedAt().atOffset(ZoneOffset.UTC)
		);
	}
}
