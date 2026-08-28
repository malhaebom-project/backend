package com.malhaebom.malhaebom.infra.async;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "malhaebom.answer-submission.async")
public record AnswerSubmissionAsyncProperties(@NotNull Duration requestTimeout) {
	public AnswerSubmissionAsyncProperties {
		if (requestTimeout != null
			&& requestTimeout.compareTo(Duration.ofSeconds(1)) < 0) {
			throw new IllegalArgumentException("답변 제출 Servlet 타임아웃은 1초 이상이어야 합니다.");
		}
	}
}
