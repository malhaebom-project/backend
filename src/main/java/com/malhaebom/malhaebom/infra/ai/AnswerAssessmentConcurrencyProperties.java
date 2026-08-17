package com.malhaebom.malhaebom.infra.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "malhaebom.answer-assessment")
public record AnswerAssessmentConcurrencyProperties(
	int maxConcurrentRequests
) {

	public AnswerAssessmentConcurrencyProperties {
		if (maxConcurrentRequests < 1) {
			throw new IllegalArgumentException(
				"답변 채점 최대 동시 요청 수는 1 이상이어야 합니다."
			);
		}
	}
}
