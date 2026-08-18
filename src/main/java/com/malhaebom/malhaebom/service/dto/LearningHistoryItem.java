package com.malhaebom.malhaebom.service.dto;

import java.time.LocalDateTime;

import com.malhaebom.malhaebom.domain.learning.Difficulty;

public record LearningHistoryItem(
	Long sessionId,
	String topicName,
	Difficulty difficulty,
	int questionCount,
	int correctCount,
	double correctRate,
	long studySeconds,
	LocalDateTime completedAt
) {
}
