package com.malhaebom.malhaebom.infra.async;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "malhaebom.speech.async")
public record SpeechAnswerAsyncProperties(
	@NotNull Duration requestTimeout,
	@Min(1) int maxConcurrentRequests
) {

	public SpeechAnswerAsyncProperties {
		if (requestTimeout != null
			&& requestTimeout.compareTo(Duration.ofSeconds(1)) < 0) {
			throw new IllegalArgumentException(
				"음성 변환 Servlet 타임아웃은 1초 이상이어야 합니다."
			);
		}
	}
}
