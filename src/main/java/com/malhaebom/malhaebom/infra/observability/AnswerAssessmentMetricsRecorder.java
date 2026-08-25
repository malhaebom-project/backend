package com.malhaebom.malhaebom.infra.observability;

import java.time.Duration;
import java.util.function.IntSupplier;

public interface AnswerAssessmentMetricsRecorder {

	void bind(
		IntSupplier queuedRequests,
		int queueCapacity
	);

	void recordAccepted();

	void recordRejected();

	void recordCompleted();

	void recordFailed();

	void recordQueued();

	void recordPromoted();

	void recordQueueFull();

	void recordQueueTimeout();

	void recordQueueCancelled();

	void recordQueueWait(QueueWaitResult result, Duration duration);

	enum QueueWaitResult {
		PROMOTED("promoted"),
		TIMEOUT("timeout"),
		CANCELLED("cancelled"),
		SHUTDOWN("shutdown");

		private final String tagValue;

		QueueWaitResult(String tagValue) {
			this.tagValue = tagValue;
		}

		public String tagValue() {
			return tagValue;
		}
	}
}
