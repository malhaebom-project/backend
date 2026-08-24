package com.malhaebom.malhaebom.infra.observability;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntSupplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.stereotype.Component;

@Component
public class MicrometerAnswerAssessmentMetricsRecorder
	implements AnswerAssessmentMetricsRecorder {

	private static final String METRIC_PREFIX =
		"malhaebom.answer.assessment.";

	private final MeterRegistry meterRegistry;
	private final Counter acceptedRequests;
	private final Counter rejectedRequests;
	private final Counter completedRequests;
	private final Counter failedRequests;
	private final Counter queuedAdmissions;
	private final Counter promotedRequests;
	private final Counter queueFullRequests;
	private final Counter queueTimeoutRequests;
	private final Counter queueCancelledRequests;
	private final Map<QueueWaitResult, Timer> queueWaitTimers =
		new EnumMap<>(QueueWaitResult.class);

	private IntSupplier activeRequests;
	private IntSupplier queuedRequests;
	private int concurrentLimit;
	private int queueCapacity;
	private boolean bound;

	public MicrometerAnswerAssessmentMetricsRecorder(
		MeterRegistry meterRegistry
	) {
		this.meterRegistry = Objects.requireNonNull(
			meterRegistry,
			"MeterRegistry는 null일 수 없습니다."
		);
		acceptedRequests = counter("accepted");
		rejectedRequests = counter("rejected");
		completedRequests = counter("completed");
		failedRequests = counter("failed");
		queuedAdmissions = counter("queued");
		promotedRequests = counter("queue.promoted");
		queueFullRequests = counter("queue.full");
		queueTimeoutRequests = counter("queue.timeout");
		queueCancelledRequests = counter("queue.cancelled");
		for (QueueWaitResult result : QueueWaitResult.values()) {
			queueWaitTimers.put(result, Timer.builder(
				METRIC_PREFIX + "queue.wait"
			).tag("result", result.tagValue()).register(meterRegistry));
		}
	}

	@Override
	public synchronized void bind(
		IntSupplier activeRequests,
		int concurrentLimit,
		IntSupplier queuedRequests,
		int queueCapacity
	) {
		if (bound) {
			throw new IllegalStateException("답안 평가 지표는 이미 연결되었습니다.");
		}
		this.activeRequests = Objects.requireNonNull(activeRequests);
		this.queuedRequests = Objects.requireNonNull(queuedRequests);
		this.concurrentLimit = concurrentLimit;
		this.queueCapacity = queueCapacity;
		Gauge.builder(METRIC_PREFIX + "active", this, recorder ->
			recorder.activeRequests.getAsInt()
		).register(meterRegistry);
		Gauge.builder(METRIC_PREFIX + "limit", this, recorder ->
			recorder.concurrentLimit
		).register(meterRegistry);
		Gauge.builder(METRIC_PREFIX + "queue.size", this, recorder ->
			recorder.queuedRequests.getAsInt()
		).register(meterRegistry);
		Gauge.builder(METRIC_PREFIX + "queue.capacity", this, recorder ->
			recorder.queueCapacity
		).register(meterRegistry);
		bound = true;
	}

	@Override
	public void recordAccepted() {
		acceptedRequests.increment();
	}

	@Override
	public void recordRejected() {
		rejectedRequests.increment();
	}

	@Override
	public void recordCompleted() {
		completedRequests.increment();
	}

	@Override
	public void recordFailed() {
		failedRequests.increment();
	}

	@Override
	public void recordQueued() {
		queuedAdmissions.increment();
	}

	@Override
	public void recordPromoted() {
		promotedRequests.increment();
	}

	@Override
	public void recordQueueFull() {
		queueFullRequests.increment();
	}

	@Override
	public void recordQueueTimeout() {
		queueTimeoutRequests.increment();
	}

	@Override
	public void recordQueueCancelled() {
		queueCancelledRequests.increment();
	}

	@Override
	public void recordQueueWait(
		QueueWaitResult result,
		Duration duration
	) {
		queueWaitTimers.get(result).record(duration);
	}

	private Counter counter(String name) {
		return meterRegistry.counter(METRIC_PREFIX + name);
	}
}
