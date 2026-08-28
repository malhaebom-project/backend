package com.malhaebom.malhaebom.service.dto;

import java.util.List;

public record LearningStatistics(
	long totalSessionCount,
	long totalStudySeconds,
	double averageCorrectRate,
	int consecutiveStudyDays,
	List<TopicStatistics> topicStatistics
) {}
