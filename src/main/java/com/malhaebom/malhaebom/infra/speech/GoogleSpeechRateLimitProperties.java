package com.malhaebom.malhaebom.infra.speech;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "gcp.stt.rate-limit")
public record GoogleSpeechRateLimitProperties(long requestsPerMinute) {
	@ConstructorBinding
	public GoogleSpeechRateLimitProperties {
		if (requestsPerMinute < 1) {
			throw new IllegalArgumentException("Google STT 분당 요청 한도는 1 이상이어야 합니다.");
		}
	}
}
