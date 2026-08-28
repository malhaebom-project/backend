package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.service.dto.LearningHistoryItem;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record LearningHistoryItemResponse(
	Long sessionId,
	String topicName,
	Difficulty difficulty,
	int questionCount,
	int correctCount,
	double correctRate,
	long studySeconds,
	OffsetDateTime completedAt
) {
	public static LearningHistoryItemResponse from(LearningHistoryItem item) {
		return new LearningHistoryItemResponse(
			item.sessionId(),
			item.topicName(),
			item.difficulty(),
			item.questionCount(),
			item.correctCount(),
			item.correctRate(),
			item.studySeconds(),
			item.completedAt().atOffset(ZoneOffset.UTC)
		);
	}
}
