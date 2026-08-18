package com.malhaebom.malhaebom.presentation.dto;

import java.util.List;

import com.malhaebom.malhaebom.service.dto.LearningStatistics;

public record LearningStatisticsResponse(
	long totalSessionCount,
	long totalStudySeconds,
	double averageCorrectRate,
	int consecutiveStudyDays,
	List<TopicStatisticsResponse> topicStatistics
) {

	public static LearningStatisticsResponse from(
		LearningStatistics statistics
	) {
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
