package com.malhaebom.malhaebom.infra.ai;

import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class ExecutorAnswerAssessmentQueueTimeoutScheduler
	implements AnswerAssessmentQueueTimeoutScheduler, AutoCloseable {

	private final ScheduledThreadPoolExecutor executor;

	ExecutorAnswerAssessmentQueueTimeoutScheduler() {
		executor = new ScheduledThreadPoolExecutor(1, runnable -> {
			Thread thread = new Thread(
				runnable,
				"answer-assessment-queue-timeout"
			);
			thread.setDaemon(true);
			return thread;
		});
		executor.setRemoveOnCancelPolicy(true);
		executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
	}

	@Override
	public TimeoutHandle schedule(Runnable task, Duration delay) {
		var future = executor.schedule(
			task,
			delay.toNanos(),
			TimeUnit.NANOSECONDS
		);
		return () -> future.cancel(false);
	}

	@Override
	public void close() {
		executor.shutdownNow();
	}
}
