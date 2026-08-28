package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.service.dto.LearningHistoryItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "완료된 학습 세션 이력")
public record LearningHistoryItemResponse(
	Long sessionId,
	String topicName,
	Difficulty difficulty,
	int questionCount,
	int correctCount,
	double correctRate,
	long studySeconds,
	@Schema(
		description = "학습 완료 시각. UTC의 ISO 8601 형식으로 반환됩니다.",
		example = "2026-08-01T01:30:00Z",
		format = "date-time"
	)
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
