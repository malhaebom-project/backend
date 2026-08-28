package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "현재 학습 세션 상태")
public record LearningSessionResponse(
	@Schema(description = "학습 세션 ID", example = "10")
	Long sessionId,
	@Schema(description = "학습 세션 상태", example = "IN_PROGRESS")
	LearningSessionStatus status,
	@Schema(description = "현재 문제의 0 기반 인덱스", example = "3")
	int currentQuestionIndex,
	@Schema(description = "전체 문제 수", example = "10")
	int questionCount,
	@Schema(description = "현재까지 맞힌 문제 수", example = "2")
	int correctCount,
	@Schema(description = "학습 시작 시각 (UTC)", example = "2026-08-01T01:00:00Z", format = "date-time")
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
