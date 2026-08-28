package com.malhaebom.malhaebom.domain.learning.repository.projection;

import java.time.LocalDateTime;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;

public interface LearningHistoryProjection {
	Long getSessionId();

	LearningTopic getTopic();

	Difficulty getDifficulty();

	Long getQuestionCount();

	Long getCorrectCount();

	LocalDateTime getStartedAt();

	LocalDateTime getCompletedAt();
}
