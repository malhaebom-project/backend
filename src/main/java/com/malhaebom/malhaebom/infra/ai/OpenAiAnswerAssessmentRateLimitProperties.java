package com.malhaebom.malhaebom.infra.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "malhaebom.answer-assessment.rate-limit")
public record OpenAiAnswerAssessmentRateLimitProperties(long requestsPerMinute, long tokensPerMinute, long tokensPerRequest) {
	@ConstructorBinding
	public OpenAiAnswerAssessmentRateLimitProperties {
		if (requestsPerMinute < 1) {
			throw new IllegalArgumentException("OpenAI 분당 요청 한도는 1 이상이어야 합니다.");
		}
		if (tokensPerMinute < 1) {
			throw new IllegalArgumentException("OpenAI 분당 토큰 한도는 1 이상이어야 합니다.");
		}
		if (tokensPerRequest < 1 || tokensPerRequest > tokensPerMinute) {
			throw new IllegalArgumentException("OpenAI 요청당 토큰은 1 이상이며 분당 토큰 한도 이하여야 합니다.");
		}
	}
}
