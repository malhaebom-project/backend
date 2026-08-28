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
	@Min(1) int maxConcurrentRequests,
	@NotNull Duration processingLease,
	@NotNull Duration shutdownDrainTimeout
) {
	public SpeechAnswerAsyncProperties {
		if (requestTimeout != null
			&& requestTimeout.compareTo(Duration.ofSeconds(1)) < 0) {
			throw new IllegalArgumentException("음성 변환 Servlet 타임아웃은 1초 이상이어야 합니다.");
		}

		if (requestTimeout != null
			&& processingLease != null
			&& processingLease.compareTo(requestTimeout) <= 0) {
			throw new IllegalArgumentException("음성 변환 처리 임대 시간은 Servlet 타임아웃보다 길어야 합니다.");
		}

		if (shutdownDrainTimeout != null
			&& (shutdownDrainTimeout.isZero()
				|| shutdownDrainTimeout.isNegative())) {
			throw new IllegalArgumentException("음성 변환 종료 대기 시간은 0초보다 길어야 합니다.");
		}
	}
}
