package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.service.dto.LearningStatistics;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "자녀의 누적 학습 통계")
public record LearningStatisticsResponse(
	@Schema(description = "완료한 전체 학습 세션 수", example = "12")
	long totalSessionCount,
	@Schema(description = "누적 학습 시간(초)", example = "5040")
	long totalStudySeconds,
	@Schema(description = "전체 문제 기준 평균 정답률", example = "82.5")
	double averageCorrectRate,
	@Schema(description = "오늘을 포함한 연속 학습 일수", example = "5")
	int consecutiveStudyDays,
	@Schema(description = "학습 주제별 통계")
	List<TopicStatisticsResponse> topicStatistics
) {
	public static LearningStatisticsResponse from(LearningStatistics statistics) {
		return new LearningStatisticsResponse(
			statistics.totalSessionCount(),
			statistics.totalStudySeconds(),
			statistics.averageCorrectRate(),
			statistics.consecutiveStudyDays(),
			statistics.topicStatistics().stream()
				.map(TopicStatisticsResponse::from)
				.toList()
		);
	}
}
