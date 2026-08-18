package com.malhaebom.malhaebom.service.dto;

import com.malhaebom.malhaebom.domain.learning.LearningTopic;

public interface TopicStatisticsProjection {

	LearningTopic getTopic();

	Long getQuestionCount();

	Long getCorrectCount();
}
