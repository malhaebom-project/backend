package com.malhaebom.malhaebom.infra.ai;

import java.time.Duration;

@FunctionalInterface
public interface AnswerAssessmentQueueTimeoutScheduler {
	TimeoutHandle schedule(Runnable task, Duration delay);

	@FunctionalInterface
	interface TimeoutHandle {
		void cancel();
	}
}
