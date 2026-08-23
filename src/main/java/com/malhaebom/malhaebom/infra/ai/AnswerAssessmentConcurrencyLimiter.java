package com.malhaebom.malhaebom.infra.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;

@Component
public class AnswerAssessmentConcurrencyLimiter {

	private static final String METRIC_PREFIX =
		"malhaebom.answer.assessment.";

	private final Object lock = new Object();
	private final int maxConcurrentRequests;
	private final int queueCapacity;
	private final Duration maxQueueWait;
	private final AnswerAssessmentQueueTimeoutScheduler timeoutScheduler;
	private final boolean ownsTimeoutScheduler;
	private final LongSupplier nanoTime;
	private final Set<QueueEntry> queue = new LinkedHashSet<>();
	private final AtomicInteger activeRequests = new AtomicInteger();
	private final AtomicInteger queuedRequests = new AtomicInteger();
	private final Counter acceptedRequests;
	private final Counter rejectedRequests;
	private final Counter completedRequests;
	private final Counter failedRequests;
	private final Counter queuedAdmissions;
	private final Counter promotedRequests;
	private final Counter queueFullRequests;
	private final Counter queueTimeoutRequests;
	private final Counter queueCancelledRequests;
	private final Timer promotedWait;
	private final Timer timeoutWait;
	private final Timer cancelledWait;
	private final Timer shutdownWait;

	private boolean accepting = true;

	@Autowired
	public AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties,
		MeterRegistry meterRegistry,
		AnswerAssessmentQueueTimeoutScheduler timeoutScheduler
	) {
		this(
			properties,
			meterRegistry,
			timeoutScheduler,
			System::nanoTime,
			false
		);
	}

	public AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties,
		MeterRegistry meterRegistry
	) {
		this(
			properties,
			meterRegistry,
			new ExecutorAnswerAssessmentQueueTimeoutScheduler(),
			System::nanoTime,
			true
		);
	}

	AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties,
		MeterRegistry meterRegistry,
		AnswerAssessmentQueueTimeoutScheduler timeoutScheduler,
		LongSupplier nanoTime
	) {
		this(properties, meterRegistry, timeoutScheduler, nanoTime, false);
	}

	private AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties,
		MeterRegistry meterRegistry,
		AnswerAssessmentQueueTimeoutScheduler timeoutScheduler,
		LongSupplier nanoTime,
		boolean ownsTimeoutScheduler
	) {
		Objects.requireNonNull(properties, "동시성 설정은 null일 수 없습니다.");
		Objects.requireNonNull(meterRegistry, "MeterRegistry는 null일 수 없습니다.");
		this.timeoutScheduler = Objects.requireNonNull(
			timeoutScheduler,
			"대기열 timeout scheduler는 null일 수 없습니다."
		);
		this.nanoTime = Objects.requireNonNull(
			nanoTime,
			"단조 시간 공급자는 null일 수 없습니다."
		);
		this.ownsTimeoutScheduler = ownsTimeoutScheduler;
		maxConcurrentRequests = properties.maxConcurrentRequests();
		queueCapacity = properties.queueCapacity();
		maxQueueWait = properties.maxQueueWait();

		acceptedRequests = meterRegistry.counter(METRIC_PREFIX + "accepted");
		rejectedRequests = meterRegistry.counter(METRIC_PREFIX + "rejected");
		completedRequests = meterRegistry.counter(METRIC_PREFIX + "completed");
		failedRequests = meterRegistry.counter(METRIC_PREFIX + "failed");
		queuedAdmissions = meterRegistry.counter(METRIC_PREFIX + "queued");
		promotedRequests = meterRegistry.counter(
			METRIC_PREFIX + "queue.promoted"
		);
		queueFullRequests = meterRegistry.counter(METRIC_PREFIX + "queue.full");
		queueTimeoutRequests = meterRegistry.counter(
			METRIC_PREFIX + "queue.timeout"
		);
		queueCancelledRequests = meterRegistry.counter(
			METRIC_PREFIX + "queue.cancelled"
		);
		promotedWait = queueWaitTimer(meterRegistry, "promoted");
		timeoutWait = queueWaitTimer(meterRegistry, "timeout");
		cancelledWait = queueWaitTimer(meterRegistry, "cancelled");
		shutdownWait = queueWaitTimer(meterRegistry, "shutdown");

		Gauge.builder(
			METRIC_PREFIX + "active",
			activeRequests,
			AtomicInteger::get
		).register(meterRegistry);
		Gauge.builder(
			METRIC_PREFIX + "limit",
			this,
			limiter -> limiter.maxConcurrentRequests
		).register(meterRegistry);
		Gauge.builder(
			METRIC_PREFIX + "queue.size",
			queuedRequests,
			AtomicInteger::get
		).register(meterRegistry);
		Gauge.builder(
			METRIC_PREFIX + "queue.capacity",
			this,
			limiter -> limiter.queueCapacity
		).register(meterRegistry);
	}

	public AnswerAssessmentTask execute(
		Supplier<AnswerAssessmentTask> taskSupplier
	) {
		Objects.requireNonNull(
			taskSupplier,
			"제한할 작업은 null일 수 없습니다."
		);

		QueueEntry entry = new QueueEntry(taskSupplier, nanoTime.getAsLong());
		Admission admission;
		synchronized (lock) {
			if (!accepting) {
				entry.state = State.TERMINAL;
				admission = Admission.SHUTDOWN;
			} else if (
				activeRequests.get() < maxConcurrentRequests
					&& queue.isEmpty()
			) {
				entry.state = State.STARTING;
				activeRequests.incrementAndGet();
				admission = Admission.START;
			} else if (queue.size() < queueCapacity) {
				try {
					entry.timeoutHandle = timeoutScheduler.schedule(
						() -> timeout(entry),
						maxQueueWait
					);
					queue.add(entry);
					queuedRequests.incrementAndGet();
					queuedAdmissions.increment();
					admission = Admission.QUEUE;
				} catch (RuntimeException exception) {
					entry.state = State.TERMINAL;
					entry.schedulingFailure = exception;
					admission = Admission.SCHEDULER_FAILURE;
				}
			} else {
				admission = Admission.FULL;
			}
		}

		switch (admission) {
			case START -> start(entry);
			case QUEUE -> {
			}
			case FULL -> rejectFull(entry);
			case SHUTDOWN -> entry.result.completeExceptionally(
				shutdownException()
			);
			case SCHEDULER_FAILURE -> failScheduling(entry);
		}
		return entry.task;
	}

	@PreDestroy
	void shutdown() {
		List<QueueEntry> shutdownEntries = new ArrayList<>();
		synchronized (lock) {
			if (!accepting) {
				return;
			}
			accepting = false;
			for (QueueEntry entry : queue) {
				entry.state = State.TERMINAL;
				cancelTimeout(entry);
				shutdownEntries.add(entry);
			}
			queue.clear();
			queuedRequests.set(0);
		}

		for (QueueEntry entry : shutdownEntries) {
			recordWait(entry, shutdownWait);
			entry.result.completeExceptionally(shutdownException());
		}
		if (
			ownsTimeoutScheduler
				&& timeoutScheduler instanceof AutoCloseable closeable
		) {
			try {
				closeable.close();
			} catch (Exception exception) {
				throw new IllegalStateException(
					"답안 평가 대기열 scheduler를 종료할 수 없습니다.",
					exception
				);
			}
		}
	}

	private void start(QueueEntry entry) {
		acceptedRequests.increment();
		AnswerAssessmentTask activeTask;
		try {
			activeTask = Objects.requireNonNull(
				entry.taskSupplier.get(),
				"제한된 작업은 null을 반환할 수 없습니다."
			);
		} catch (RuntimeException exception) {
			finishActive(entry, null, exception);
			return;
		}

		boolean cancelRequested;
		synchronized (lock) {
			if (entry.state != State.STARTING) {
				return;
			}
			entry.activeTask = activeTask;
			entry.state = State.ACTIVE;
			cancelRequested = entry.cancelRequested;
		}

		activeTask.result().whenComplete(
			(result, exception) -> finishActive(entry, result, exception)
		);
		if (cancelRequested) {
			activeTask.cancel();
		}
	}

	private void finishActive(
		QueueEntry entry,
		AnswerAssessment result,
		Throwable exception
	) {
		QueueEntry promoted;
		synchronized (lock) {
			if (entry.state != State.STARTING && entry.state != State.ACTIVE) {
				return;
			}
			entry.state = State.TERMINAL;
			activeRequests.decrementAndGet();
			promoted = accepting ? pollQueuedForPromotion() : null;
			if (promoted != null) {
				promoted.state = State.STARTING;
				activeRequests.incrementAndGet();
			}
		}

		if (exception == null) {
			completedRequests.increment();
			entry.result.complete(result);
		} else {
			failedRequests.increment();
			entry.result.completeExceptionally(exception);
		}
		if (promoted != null) {
			promotedRequests.increment();
			recordWait(promoted, promotedWait);
			start(promoted);
		}
	}

	private QueueEntry pollQueuedForPromotion() {
		Iterator<QueueEntry> iterator = queue.iterator();
		while (iterator.hasNext()) {
			QueueEntry candidate = iterator.next();
			iterator.remove();
			queuedRequests.decrementAndGet();
			if (candidate.state == State.QUEUED) {
				cancelTimeout(candidate);
				return candidate;
			}
		}
		return null;
	}

	private boolean cancel(QueueEntry entry) {
		AnswerAssessmentTask activeTask = null;
		boolean cancelledWhileQueued = false;
		synchronized (lock) {
			switch (entry.state) {
				case QUEUED -> {
					if (!queue.remove(entry)) {
						return false;
					}
					cancelTimeout(entry);
					queuedRequests.decrementAndGet();
					entry.state = State.TERMINAL;
					cancelledWhileQueued = true;
				}
				case STARTING -> {
					entry.cancelRequested = true;
					return true;
				}
				case ACTIVE -> activeTask = entry.activeTask;
				case TERMINAL -> {
					return false;
				}
			}
		}

		if (cancelledWhileQueued) {
			queueCancelledRequests.increment();
			recordWait(entry, cancelledWait);
			return entry.result.cancel(false);
		}
		return activeTask != null && activeTask.cancel();
	}

	private void timeout(QueueEntry entry) {
		synchronized (lock) {
			if (entry.state != State.QUEUED || !queue.remove(entry)) {
				return;
			}
			queuedRequests.decrementAndGet();
			entry.state = State.TERMINAL;
		}
		queueTimeoutRequests.increment();
		rejectedRequests.increment();
		recordWait(entry, timeoutWait);
		entry.result.completeExceptionally(overloadedException());
	}

	private void failScheduling(QueueEntry entry) {
		entry.result.completeExceptionally(
			new IllegalStateException(
				"답안 평가 대기열 timeout을 예약할 수 없습니다.",
				entry.schedulingFailure
			)
		);
	}

	private void rejectFull(QueueEntry entry) {
		queueFullRequests.increment();
		rejectedRequests.increment();
		entry.state = State.TERMINAL;
		entry.result.completeExceptionally(overloadedException());
	}

	private void cancelTimeout(QueueEntry entry) {
		AnswerAssessmentQueueTimeoutScheduler.TimeoutHandle handle =
			entry.timeoutHandle;
		if (handle != null) {
			handle.cancel();
		}
	}

	private void recordWait(QueueEntry entry, Timer timer) {
		long elapsed = Math.max(0, nanoTime.getAsLong() - entry.enqueuedAtNanos);
		timer.record(Duration.ofNanos(elapsed));
	}

	private Timer queueWaitTimer(MeterRegistry meterRegistry, String result) {
		return Timer.builder(METRIC_PREFIX + "queue.wait")
			.tag("result", result)
			.register(meterRegistry);
	}

	private ApiException overloadedException() {
		return new ApiException(ErrorCode.ANSWER_ASSESSMENT_OVERLOADED);
	}

	private IllegalStateException shutdownException() {
		return new IllegalStateException(
			"답안 평가 동시성 제한기가 종료되었습니다."
		);
	}

	private enum Admission {
		START,
		QUEUE,
		FULL,
		SHUTDOWN,
		SCHEDULER_FAILURE
	}

	private enum State {
		QUEUED,
		STARTING,
		ACTIVE,
		TERMINAL
	}

	private final class QueueEntry {

		private final Supplier<AnswerAssessmentTask> taskSupplier;
		private final CompletableFuture<AnswerAssessment> result =
			new CompletableFuture<>();
		private final AnswerAssessmentTask task = new AnswerAssessmentTask(
			result,
			() -> cancel(this)
		);
		private final long enqueuedAtNanos;
		private State state = State.QUEUED;
		private AnswerAssessmentQueueTimeoutScheduler.TimeoutHandle timeoutHandle;
		private AnswerAssessmentTask activeTask;
		private RuntimeException schedulingFailure;
		private boolean cancelRequested;

		private QueueEntry(
			Supplier<AnswerAssessmentTask> taskSupplier,
			long enqueuedAtNanos
		) {
			this.taskSupplier = taskSupplier;
			this.enqueuedAtNanos = enqueuedAtNanos;
		}
	}
}
