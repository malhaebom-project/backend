package com.malhaebom.malhaebom.domain.learning.repository.projection;

import com.malhaebom.malhaebom.domain.learning.LearningTopic;

public interface TopicStatisticsProjection {

	LearningTopic getTopic();

	Long getQuestionCount();

	Long getCorrectCount();
}
