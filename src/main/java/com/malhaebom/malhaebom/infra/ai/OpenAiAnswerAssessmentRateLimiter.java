package com.malhaebom.malhaebom.infra.ai;

import java.time.Duration;
import java.util.Objects;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.infra.observability.ProviderRateLimitMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.ProviderRateLimitMetricsRecorder.Result;

@Component
public class OpenAiAnswerAssessmentRateLimiter {
	static final String PROVIDER = "openai";

	private final Bucket requestBucket;
	private final Bucket tokenBucket;
	private final long tokensPerRequest;
	private final ProviderRateLimitMetricsRecorder metrics;

	public OpenAiAnswerAssessmentRateLimiter(
		OpenAiAnswerAssessmentRateLimitProperties properties,
		ProviderRateLimitMetricsRecorder metrics,
		TimeMeter timeMeter
	) {
		Objects.requireNonNull(properties, "OpenAI rate limit 설정은 null일 수 없습니다.");
		this.metrics = Objects.requireNonNull(
			metrics,
			"provider rate limit 지표 기록기는 null일 수 없습니다."
		);
		tokensPerRequest = properties.tokensPerRequest();
		requestBucket = bucket(properties.requestsPerMinute(), timeMeter);
		tokenBucket = bucket(properties.tokensPerMinute(), timeMeter);
		metrics.bindCapacity(PROVIDER, "requests", properties.requestsPerMinute());
		metrics.bindCapacity(PROVIDER, "tokens", properties.tokensPerMinute());
		metrics.bindAvailable(PROVIDER, "requests", requestBucket::getAvailableTokens);
		metrics.bindAvailable(PROVIDER, "tokens", tokenBucket::getAvailableTokens);
	}

	AcquireResult tryAcquire() {
		ConsumptionProbe requestProbe = requestBucket
			.tryConsumeAndReturnRemaining(1);
		if (!requestProbe.isConsumed()) {
			return AcquireResult.delayed(
				Duration.ofNanos(requestProbe.getNanosToWaitForRefill())
			);
		}

		ConsumptionProbe tokenProbe = tokenBucket
			.tryConsumeAndReturnRemaining(tokensPerRequest);
		if (!tokenProbe.isConsumed()) {
			requestBucket.addTokens(1);
			return AcquireResult.delayed(
				Duration.ofNanos(tokenProbe.getNanosToWaitForRefill())
			);
		}

		metrics.record(PROVIDER, Result.ALLOWED);
		return AcquireResult.admitted();
	}

	void recordDelayed() {
		metrics.record(PROVIDER, Result.DELAYED);
	}

	void recordRejected() {
		metrics.record(PROVIDER, Result.REJECTED);
	}

	private Bucket bucket(long capacity, TimeMeter timeMeter) {
		Bandwidth bandwidth = Bandwidth.builder()
			.capacity(capacity)
			.refillGreedy(capacity, Duration.ofMinutes(1))
			.build();
		return Bucket.builder()
			.addLimit(bandwidth)
			.withCustomTimePrecision(Objects.requireNonNull(timeMeter))
			.build();
	}

	record AcquireResult(boolean allowed, Duration retryAfter) {
		static AcquireResult admitted() {
			return new AcquireResult(true, Duration.ZERO);
		}

		static AcquireResult delayed(Duration retryAfter) {
			return new AcquireResult(false, retryAfter);
		}
	}
}
