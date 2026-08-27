package com.malhaebom.malhaebom.infra.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import com.malhaebom.malhaebom.infra.observability.MicrometerProviderRateLimitMetricsRecorder;

class OpenAiAnswerAssessmentRateLimiterTest {

	@Test
	void 요청마다_고정_토큰을_차감하고_greedy_refill_후_다시_허용한다() {
		Fixture fixture = fixture(2, 6_000, 3_000);

		assertTrue(fixture.limiter().tryAcquire().allowed());
		assertTrue(fixture.limiter().tryAcquire().allowed());
		assertFalse(fixture.limiter().tryAcquire().allowed());
		assertEquals(2.0, capacity(fixture, "requests"));
		assertEquals(6_000.0, capacity(fixture, "tokens"));
		assertEquals(0.0, available(fixture, "requests"));
		assertEquals(0.0, available(fixture, "tokens"));

		fixture.time().advance(Duration.ofSeconds(30));

		assertTrue(fixture.limiter().tryAcquire().allowed());
		assertEquals(3.0, requests(fixture, "allowed"));
	}

	@Test
	void token_bucket만_부족하면_먼저_획득한_request_token을_복구한다() {
		Fixture fixture = fixture(2, 3_000, 3_000);
		assertTrue(fixture.limiter().tryAcquire().allowed());

		OpenAiAnswerAssessmentRateLimiter.AcquireResult rejected =
			fixture.limiter().tryAcquire();

		assertFalse(rejected.allowed());
		assertEquals(Duration.ofMinutes(1), rejected.retryAfter());
		assertEquals(1.0, available(fixture, "requests"));
		assertEquals(0.0, available(fixture, "tokens"));
	}

	private Fixture fixture(long rpm, long tpm, long tokensPerRequest) {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ManualTimeMeter time = new ManualTimeMeter();
		OpenAiAnswerAssessmentRateLimiter limiter =
			new OpenAiAnswerAssessmentRateLimiter(
				new OpenAiAnswerAssessmentRateLimitProperties(
					rpm, tpm, tokensPerRequest),
				new MicrometerProviderRateLimitMetricsRecorder(registry),
				time);
		return new Fixture(limiter, registry, time);
	}

	private double available(Fixture fixture, String quota) {
		return fixture.registry()
			.get("malhaebom.ai.provider.rate.limit.available")
			.tag("provider", "openai")
			.tag("quota", quota)
			.gauge()
			.value();
	}

	private double capacity(Fixture fixture, String quota) {
		return fixture.registry()
			.get("malhaebom.ai.provider.rate.limit.capacity")
			.tag("provider", "openai")
			.tag("quota", quota)
			.gauge()
			.value();
	}

	private double requests(Fixture fixture, String result) {
		return fixture.registry()
			.get("malhaebom.ai.provider.rate.limit.requests")
			.tag("provider", "openai")
			.tag("result", result)
			.counter()
			.count();
	}

	private record Fixture(
		OpenAiAnswerAssessmentRateLimiter limiter,
		SimpleMeterRegistry registry,
		ManualTimeMeter time
	) {
	}

	static final class ManualTimeMeter implements TimeMeter {

		private final AtomicLong nanos = new AtomicLong();

		@Override
		public long currentTimeNanos() {
			return nanos.get();
		}

		@Override
		public boolean isWallClockBased() {
			return false;
		}

		void advance(Duration duration) {
			nanos.addAndGet(duration.toNanos());
		}
	}
}
