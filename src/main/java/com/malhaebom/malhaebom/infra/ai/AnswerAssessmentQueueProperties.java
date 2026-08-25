package com.malhaebom.malhaebom.infra.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "malhaebom.answer-assessment")
public record AnswerAssessmentQueueProperties(
	int queueCapacity,
	Duration maxQueueWait
) {

	@ConstructorBinding
	public AnswerAssessmentQueueProperties {
		if (queueCapacity < 0) {
			throw new IllegalArgumentException(
				"답변 채점 대기열 용량은 0 이상이어야 합니다."
			);
		}
		if (queueCapacity > 0
			&& (maxQueueWait == null || maxQueueWait.isZero()
				|| maxQueueWait.isNegative())) {
			throw new IllegalArgumentException(
				"답변 채점 최대 대기 시간은 0보다 커야 합니다."
			);
		}
	}
}
