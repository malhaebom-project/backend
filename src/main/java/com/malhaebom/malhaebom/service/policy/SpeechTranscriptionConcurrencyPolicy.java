package com.malhaebom.malhaebom.service.policy;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpeechTranscriptionConcurrencyPolicy {
	private final Semaphore permits;
	private final int maxConcurrentRequests;

	public SpeechTranscriptionConcurrencyPolicy(int maxConcurrentRequests) {
		if (maxConcurrentRequests < 1) {
			throw new IllegalArgumentException(
				"최대 동시 음성 변환 요청 수는 1 이상이어야 합니다."
			);
		}
		this.maxConcurrentRequests = maxConcurrentRequests;
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

		private final SpeechTranscriptionConcurrencyPolicy policy;
		private final AtomicBoolean released = new AtomicBoolean();

		private Permit(SpeechTranscriptionConcurrencyPolicy policy) {
			this.policy = policy;
		}

		public void release() {
			if (!released.compareAndSet(false, true)) {
				return;
			}
			policy.release();
		}
	}
}
