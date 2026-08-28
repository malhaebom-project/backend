package com.malhaebom.malhaebom.infra.speech;

import java.time.Duration;
import java.util.Objects;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;

import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.infra.observability.ProviderRateLimitMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.ProviderRateLimitMetricsRecorder.Result;
import com.malhaebom.malhaebom.service.port.SpeechTranscriptionRateLimit;

@Component
public class SpeechTranscriptionRateLimiter
	implements SpeechTranscriptionRateLimit {

	static final String PROVIDER = "gcp-stt";

	private final Bucket requestBucket;
	private final ProviderRateLimitMetricsRecorder metrics;

	public SpeechTranscriptionRateLimiter(
		GoogleSpeechRateLimitProperties properties,
		ProviderRateLimitMetricsRecorder metrics,
		TimeMeter timeMeter
	) {
		Objects.requireNonNull(properties, "Google STT rate limit 설정은 null일 수 없습니다.");
		this.metrics = Objects.requireNonNull(
			metrics,
			"provider rate limit 지표 기록기는 null일 수 없습니다."
		);
		long capacity = properties.requestsPerMinute();
		Bandwidth bandwidth = Bandwidth.builder()
			.capacity(capacity)
			.refillGreedy(capacity, Duration.ofMinutes(1))
			.build();
		requestBucket = Bucket.builder()
			.addLimit(bandwidth)
			.withCustomTimePrecision(Objects.requireNonNull(timeMeter))
			.build();
		metrics.bindCapacity(PROVIDER, "requests", capacity);
		metrics.bindAvailable(
			PROVIDER,
			"requests",
			requestBucket::getAvailableTokens
		);
	}

	@Override
	public boolean tryAcquire() {
		if (requestBucket.tryConsume(1)) {
			metrics.record(PROVIDER, Result.ALLOWED);
			return true;
		}
		metrics.record(PROVIDER, Result.REJECTED);
		return false;
	}
}
