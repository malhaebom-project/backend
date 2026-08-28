package com.malhaebom.malhaebom.service.dto;

import com.malhaebom.malhaebom.domain.learning.Difficulty;

import java.time.LocalDateTime;

public record LearningHistoryItem(
	Long sessionId,
	String topicName,
	Difficulty difficulty,
	int questionCount,
	int correctCount,
	double correctRate,
	long studySeconds,
	LocalDateTime completedAt
) {}
