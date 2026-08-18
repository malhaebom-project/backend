package com.malhaebom.malhaebom.service.policy;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "malhaebom.answer-submission")
public record AnswerSubmissionPolicyProperties(
	@NotNull Duration processingTimeout,
	@NotNull Duration processingLease
) {

	public AnswerSubmissionPolicyProperties {
		validateDuration(processingTimeout, "답변 제출 처리 제한 시간");
		validateDuration(processingLease, "답변 제출 처리 임대 시간");
		if (processingTimeout != null
			&& processingLease != null
			&& processingLease.compareTo(processingTimeout) <= 0) {
			throw new IllegalArgumentException(
				"답변 제출 처리 임대 시간은 처리 제한 시간보다 길어야 합니다."
			);
		}
	}

	private static void validateDuration(Duration duration, String fieldName) {
		if (duration != null && duration.compareTo(Duration.ofSeconds(1)) < 0) {
			throw new IllegalArgumentException(fieldName + "은 1초 이상이어야 합니다.");
		}
	}
}
