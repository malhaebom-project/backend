package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.service.dto.TopicStatistics;

public record TopicStatisticsResponse(String topicName, long questionCount, double correctRate) {
	public static TopicStatisticsResponse from(TopicStatistics statistics) {
		return new TopicStatisticsResponse(statistics.topicName(), statistics.questionCount(), statistics.correctRate());
	}
}
