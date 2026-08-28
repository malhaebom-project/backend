package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.service.dto.TopicStatistics;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학습 주제별 통계")
public record TopicStatisticsResponse(
	@Schema(description = "학습 주제 이름", example = "동물") String topicName,
	@Schema(description = "답변한 문제 수", example = "20") long questionCount,
	@Schema(description = "주제별 정답률", example = "85.0") double correctRate
) {
	public static TopicStatisticsResponse from(TopicStatistics statistics) {
		return new TopicStatisticsResponse(statistics.topicName(), statistics.questionCount(), statistics.correctRate());
	}
}
