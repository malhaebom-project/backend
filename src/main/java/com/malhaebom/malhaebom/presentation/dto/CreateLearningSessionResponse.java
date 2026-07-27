package com.malhaebom.malhaebom.presentation.dto;

import java.time.LocalDateTime;

import com.malhaebom.malhaebom.domain.Difficulty;
import com.malhaebom.malhaebom.domain.LearningSession;
import com.malhaebom.malhaebom.domain.LearningSessionStatus;

public record CreateLearningSessionResponse(
	Long sessionId,
	Long childId,
	Long topicId,
	Difficulty difficulty,
	int questionCount,
	int currentQuestionIndex,
	LearningSessionStatus status,
	LocalDateTime startedAt
) {

	public static CreateLearningSessionResponse from(LearningSession session) {
		return new CreateLearningSessionResponse(
			session.getId(),
			session.getChildId(),
			session.getTopic().getTopicId(),
			session.getDifficulty(),
			session.getQuestionCount(),
			session.getCurrentQuestionIndex(),
			session.getStatus(),
			session.getStartedAt()
		);
	}
}
