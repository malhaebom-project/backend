package com.malhaebom.malhaebom.infra.speech;

import com.malhaebom.malhaebom.infra.observability.MicrometerProviderRateLimitMetricsRecorder;
import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SpeechTranscriptionRateLimiterTest {
	@Test
	void RPM_240을_초과하면_fail_fast로_거절하고_refill_후_허용한다() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ManualTimeMeter time = new ManualTimeMeter();
		SpeechTranscriptionRateLimiter limiter =
			new SpeechTranscriptionRateLimiter(
				new GoogleSpeechRateLimitProperties(240),
				new MicrometerProviderRateLimitMetricsRecorder(registry),
				time
			);

		for (int index = 0; index < 240; index++) {
			assertTrue(limiter.tryAcquire());
		}
		assertFalse(limiter.tryAcquire());
		assertEquals(0.0, available(registry));
		assertEquals(240.0, requests(registry, "allowed"));
		assertEquals(1.0, requests(registry, "rejected"));

		time.advance(Duration.ofMillis(250));

		assertTrue(limiter.tryAcquire());
		assertEquals(241.0, requests(registry, "allowed"));
	}

	private double available(SimpleMeterRegistry registry) {
		return registry.get("malhaebom.ai.provider.rate.limit.available")
			.tag("provider", "gcp-stt")
			.tag("quota", "requests")
			.gauge()
			.value();
	}

	private double requests(SimpleMeterRegistry registry, String result) {
		return registry.get("malhaebom.ai.provider.rate.limit.requests")
			.tag("provider", "gcp-stt")
			.tag("result", result)
			.counter()
			.count();
	}

	private static final class ManualTimeMeter implements TimeMeter {
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
