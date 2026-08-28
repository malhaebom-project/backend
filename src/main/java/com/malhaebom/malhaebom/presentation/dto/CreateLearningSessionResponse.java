package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "생성된 학습 세션")
public record CreateLearningSessionResponse(
	@Schema(description = "학습 세션 ID", example = "10")
	Long sessionId,
	@Schema(description = "학습하는 자녀 프로필 ID", example = "1")
	Long childId,
	@Schema(description = "선택한 학습 주제 ID", example = "1")
	Long topicId,
	@Schema(description = "학습 난이도", example = "EASY")
	Difficulty difficulty,
	@Schema(description = "세션의 전체 문제 수", example = "10")
	int questionCount,
	@Schema(description = "현재 문제의 0 기반 인덱스", example = "0")
	int currentQuestionIndex,
	@Schema(description = "학습 세션 상태", example = "IN_PROGRESS")
	LearningSessionStatus status,
	@Schema(description = "학습 시작 시각 (UTC)", example = "2026-08-01T01:00:00Z", format = "date-time")
	OffsetDateTime startedAt
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
			session.getStartedAt().atOffset(ZoneOffset.UTC)
		);
	}
}
