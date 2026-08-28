package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학습 주제")
public record LearningTopicResponse(
	@Schema(description = "학습 주제 ID", example = "1") Long topicId,
	@Schema(description = "학습 주제 표시 이름", example = "동물") String name,
	@Schema(description = "학습 주제 코드", example = "ANIMAL") String code
) {
	public static LearningTopicResponse from(LearningTopic learningTopic) {
		return new LearningTopicResponse(learningTopic.getTopicId(), learningTopic.getName(), learningTopic.getCode());
	}
}
