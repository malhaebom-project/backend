package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "완료된 학습 세션 결과")
public record LearningSessionResultResponse(
	@Schema(description = "학습 세션 ID", example = "10")
	Long sessionId,
	@Schema(description = "전체 문제 수", example = "5")
	int questionCount,
	@Schema(description = "맞힌 문제 수", example = "4")
	int correctCount,
	@Schema(description = "정답률 (0~100)", example = "80", minimum = "0", maximum = "100")
	int correctRate,
	@Schema(description = "총 학습 시간(초)", example = "286")
	long studySeconds,
	@Schema(description = "학습 완료 시각 (UTC)", example = "2026-08-01T01:07:00Z", format = "date-time")
	OffsetDateTime completedAt
) {
	public static LearningSessionResultResponse from(LearningSession session) {
		int questionCount = session.getQuestionCount();
		int correctCount = session.getCorrectCount();

		return new LearningSessionResultResponse(
			session.getId(),
			questionCount,
			correctCount,
			calculateCorrectRate(correctCount, questionCount),
			session.getStudyDuration().getSeconds(),
			session.getCompletedAt().atOffset(ZoneOffset.UTC)
		);
	}

	private static int calculateCorrectRate(
		int correctCount,
		int questionCount
	) {
		if (questionCount == 0) {
			return 0;
		}

		return (int)Math.round(correctCount * 100.0 / questionCount);
	}
}
