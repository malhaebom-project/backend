package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.LearningTopic;

public record LearningTopicResponse(Long topicId, String name, String code) {
	public static LearningTopicResponse from(LearningTopic learningTopic) {
		return new LearningTopicResponse(learningTopic.getTopicId(), learningTopic.getName(), learningTopic.getCode());
	}
}
