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

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.observability.AnswerAssessmentMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.AnswerAssessmentMetricsRecorder.QueueWaitResult;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;

@Component
public class AnswerAssessmentConcurrencyLimiter {

	private final Object lock = new Object();
	private final int maxConcurrentRequests;
	private final int queueCapacity;
	private final Duration maxQueueWait;
	private final AnswerAssessmentQueueTimeoutScheduler timeoutScheduler;
	private final boolean ownsTimeoutScheduler;
	private final LongSupplier nanoTime;
	private final AnswerAssessmentMetricsRecorder metrics;
	private final Set<QueueEntry> queue = new LinkedHashSet<>();
	private final AtomicInteger activeRequests = new AtomicInteger();
	private final AtomicInteger queuedRequests = new AtomicInteger();

	private boolean accepting = true;

	@Autowired
	public AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties,
		AnswerAssessmentMetricsRecorder metrics,
		AnswerAssessmentQueueTimeoutScheduler timeoutScheduler
	) {
		this(
			properties,
			metrics,
			timeoutScheduler,
			System::nanoTime,
			false
		);
	}

	public AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties,
		AnswerAssessmentMetricsRecorder metrics
	) {
		this(
			properties,
			metrics,
			new ExecutorAnswerAssessmentQueueTimeoutScheduler(),
			System::nanoTime,
			true
		);
	}

	AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties,
		AnswerAssessmentMetricsRecorder metrics,
		AnswerAssessmentQueueTimeoutScheduler timeoutScheduler,
		LongSupplier nanoTime
	) {
		this(properties, metrics, timeoutScheduler, nanoTime, false);
	}

	private AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties,
		AnswerAssessmentMetricsRecorder metrics,
		AnswerAssessmentQueueTimeoutScheduler timeoutScheduler,
		LongSupplier nanoTime,
		boolean ownsTimeoutScheduler
	) {
		Objects.requireNonNull(properties, "동시성 설정은 null일 수 없습니다.");
		this.metrics = Objects.requireNonNull(
			metrics,
			"답안 평가 지표 기록기는 null일 수 없습니다."
		);
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
		metrics.bind(
			activeRequests::get,
			maxConcurrentRequests,
			queuedRequests::get,
			queueCapacity
		);
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
					metrics.recordQueued();
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
			recordWait(entry, QueueWaitResult.SHUTDOWN);
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
		metrics.recordAccepted();
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
		List<QueueEntry> expiredEntries = new ArrayList<>();
		QueueEntry promoted;
		synchronized (lock) {
			if (entry.state != State.STARTING && entry.state != State.ACTIVE) {
				return;
			}
			entry.state = State.TERMINAL;
			activeRequests.decrementAndGet();
			promoted = accepting
				? pollQueuedForPromotion(expiredEntries)
				: null;
			if (promoted != null) {
				promoted.state = State.STARTING;
				activeRequests.incrementAndGet();
			}
		}

		if (exception == null) {
			metrics.recordCompleted();
		} else {
			metrics.recordFailed();
		}
		if (promoted != null) {
			metrics.recordPromoted();
			recordWait(promoted, QueueWaitResult.PROMOTED);
			start(promoted);
		}
		for (QueueEntry expired : expiredEntries) {
			completeTimeout(expired);
		}
		if (exception == null) {
			entry.result.complete(result);
		} else {
			entry.result.completeExceptionally(exception);
		}
	}

	private QueueEntry pollQueuedForPromotion(
		List<QueueEntry> expiredEntries
	) {
		long now = nanoTime.getAsLong();
		Iterator<QueueEntry> iterator = queue.iterator();
		while (iterator.hasNext()) {
			QueueEntry candidate = iterator.next();
			iterator.remove();
			queuedRequests.decrementAndGet();
			if (candidate.state == State.QUEUED) {
				cancelTimeout(candidate);
				if (queueWaitExpired(candidate, now)) {
					candidate.state = State.TERMINAL;
					expiredEntries.add(candidate);
					continue;
				}
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
			metrics.recordQueueCancelled();
			recordWait(entry, QueueWaitResult.CANCELLED);
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
		completeTimeout(entry);
	}

	private void completeTimeout(QueueEntry entry) {
		metrics.recordQueueTimeout();
		metrics.recordRejected();
		recordWait(entry, QueueWaitResult.TIMEOUT);
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
		metrics.recordQueueFull();
		metrics.recordRejected();
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

	private boolean queueWaitExpired(QueueEntry entry, long now) {
		long elapsed = Math.max(0, now - entry.enqueuedAtNanos);
		return elapsed >= maxQueueWait.toNanos();
	}

	private void recordWait(QueueEntry entry, QueueWaitResult result) {
		long elapsed = Math.max(0, nanoTime.getAsLong() - entry.enqueuedAtNanos);
		metrics.recordQueueWait(result, Duration.ofNanos(elapsed));
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
