package com.malhaebom.malhaebom.infra.speech;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.infra.async.SpeechAnswerAsyncProperties;

@Component
public class SpeechTranscriptionConcurrencyLimiter {

	private final Semaphore permits;
	private final int maxConcurrentRequests;

	public SpeechTranscriptionConcurrencyLimiter(
		SpeechAnswerAsyncProperties properties
	) {
		maxConcurrentRequests = properties.maxConcurrentRequests();
		permits = new Semaphore(maxConcurrentRequests);
	}

	public Permit tryAcquire() {
		if (!permits.tryAcquire()) {
			return null;
		}
		return new Permit(this);
	}

	public int activeRequests() {
		return maxConcurrentRequests - permits.availablePermits();
	}

	public int maxConcurrentRequests() {
		return maxConcurrentRequests;
	}

	private void release() {
		permits.release();
	}

	public static final class Permit {

		private final SpeechTranscriptionConcurrencyLimiter limiter;
		private final AtomicBoolean released = new AtomicBoolean();

		private Permit(SpeechTranscriptionConcurrencyLimiter limiter) {
			this.limiter = limiter;
		}

		public boolean release() {
			if (!released.compareAndSet(false, true)) {
				return false;
			}
			limiter.release();
			return true;
		}
	}
}
