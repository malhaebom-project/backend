package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.service.dto.LearningHistoryItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "완료된 학습 세션 이력")
public record LearningHistoryItemResponse(
	@Schema(description = "학습 세션 ID", example = "10")
	Long sessionId,
	@Schema(description = "학습 주제 이름", example = "동물")
	String topicName,
	@Schema(description = "학습 난이도", example = "EASY")
	Difficulty difficulty,
	@Schema(description = "전체 문제 수", example = "10")
	int questionCount,
	@Schema(description = "맞힌 문제 수", example = "8")
	int correctCount,
	@Schema(description = "정답률", example = "80.0")
	double correctRate,
	@Schema(description = "학습 시간(초)", example = "420")
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
