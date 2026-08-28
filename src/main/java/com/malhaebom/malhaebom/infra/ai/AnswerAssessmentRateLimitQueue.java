package com.malhaebom.malhaebom.infra.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.infra.ai.OpenAiAnswerAssessmentRateLimiter.AcquireResult;
import com.malhaebom.malhaebom.infra.observability.AnswerAssessmentMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.AnswerAssessmentMetricsRecorder.QueueWaitResult;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;
import com.malhaebom.malhaebom.service.exception.AnswerAssessmentOverloadedException;

@Component
public class AnswerAssessmentRateLimitQueue {

	private final Object lock = new Object();
	private final int queueCapacity;
	private final Duration maxQueueWait;
	private final AnswerAssessmentQueueTimeoutScheduler timeoutScheduler;
	private final LongSupplier nanoTime;
	private final AnswerAssessmentMetricsRecorder metrics;
	private final OpenAiAnswerAssessmentRateLimiter rateLimiter;
	private final Set<QueueEntry> queue = new LinkedHashSet<>();
	private final AtomicInteger queuedRequests = new AtomicInteger();

	private boolean accepting = true;
	private AnswerAssessmentQueueTimeoutScheduler.TimeoutHandle rateRetryHandle;

	public AnswerAssessmentRateLimitQueue(
		AnswerAssessmentQueueProperties properties,
		AnswerAssessmentMetricsRecorder metrics,
		OpenAiAnswerAssessmentRateLimiter rateLimiter,
		AnswerAssessmentQueueTimeoutScheduler timeoutScheduler,
		@Qualifier("answerAssessmentNanoTime")
		LongSupplier nanoTime
	) {
		Objects.requireNonNull(properties, "대기열 설정은 null일 수 없습니다.");
		this.metrics = Objects.requireNonNull(
			metrics, "답안 평가 지표 기록기는 null일 수 없습니다.");
		this.rateLimiter = Objects.requireNonNull(
			rateLimiter, "OpenAI rate limiter는 null일 수 없습니다.");
		this.timeoutScheduler = Objects.requireNonNull(
			timeoutScheduler, "대기열 timeout scheduler는 null일 수 없습니다.");
		this.nanoTime = Objects.requireNonNull(
			nanoTime, "단조 시간 공급자는 null일 수 없습니다.");
		queueCapacity = properties.queueCapacity();
		maxQueueWait = properties.maxQueueWait();
		metrics.bind(queuedRequests::get, queueCapacity);
	}

	public AnswerAssessmentTask execute(
		Supplier<AnswerAssessmentTask> taskSupplier
	) {
		Objects.requireNonNull(taskSupplier, "제한할 작업은 null일 수 없습니다.");
		QueueEntry entry = new QueueEntry(taskSupplier, nanoTime.getAsLong());
		Admission admission;
		synchronized (lock) {
			if (!accepting) {
				entry.state = State.TERMINAL;
				admission = Admission.SHUTDOWN;
			} else if (queue.isEmpty()) {
				AcquireResult rate = rateLimiter.tryAcquire();
				if (rate.allowed()) {
					entry.state = State.STARTING;
					admission = Admission.START;
				} else {
					admission = enqueueRateDelayed(entry, rate.retryAfter());
				}
			} else {
				admission = enqueue(entry);
			}
		}

		switch (admission) {
			case START -> start(entry);
			case QUEUE -> { }
			case FULL -> rejectFull(entry);
			case RATE_REJECTED -> rejectRate(entry);
			case SHUTDOWN -> entry.result.completeExceptionally(shutdownException());
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
			cancelRateRetry();
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
	}

	private Admission enqueueRateDelayed(QueueEntry entry, Duration retryAfter) {
		if (queueCapacity == 0) {
			return Admission.RATE_REJECTED;
		}
		Admission admission = enqueue(entry);
		if (admission != Admission.QUEUE) {
			return admission;
		}
		markRateDelayed(entry);
		try {
			scheduleRateRetry(retryAfter);
			return admission;
		} catch (RuntimeException exception) {
			removeQueued(entry);
			entry.state = State.TERMINAL;
			entry.schedulingFailure = exception;
			return Admission.SCHEDULER_FAILURE;
		}
	}

	private Admission enqueue(QueueEntry entry) {
		if (queue.size() >= queueCapacity) {
			return Admission.FULL;
		}
		try {
			entry.timeoutHandle = timeoutScheduler.schedule(
				() -> timeout(entry), maxQueueWait);
			queue.add(entry);
			queuedRequests.incrementAndGet();
			metrics.recordQueued();
			return Admission.QUEUE;
		} catch (RuntimeException exception) {
			entry.state = State.TERMINAL;
			entry.schedulingFailure = exception;
			return Admission.SCHEDULER_FAILURE;
		}
	}

	private void start(QueueEntry entry) {
		metrics.recordAccepted();
		AnswerAssessmentTask activeTask;
		try {
			activeTask = Objects.requireNonNull(entry.taskSupplier.get(),
				"제한된 작업은 null을 반환할 수 없습니다.");
		} catch (RuntimeException exception) {
			finish(entry, null, exception);
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
			(result, exception) -> finish(entry, result, exception));
		if (cancelRequested) {
			activeTask.cancel();
		}
	}

	private void finish(
		QueueEntry entry,
		AnswerAssessment result,
		Throwable exception
	) {
		synchronized (lock) {
			if (entry.state != State.STARTING && entry.state != State.ACTIVE) {
				return;
			}
			entry.state = State.TERMINAL;
		}
		if (exception == null) {
			metrics.recordCompleted();
			entry.result.complete(result);
		} else {
			metrics.recordFailed();
			entry.result.completeExceptionally(exception);
		}
	}

	private void drainQueue(
		List<QueueEntry> expired,
		List<QueueEntry> promoted
	) {
		long now = nanoTime.getAsLong();
		while (!queue.isEmpty()) {
			QueueEntry candidate = queue.iterator().next();
			if (candidate.state != State.QUEUED) {
				removeQueued(candidate);
				continue;
			}
			if (queueWaitExpired(candidate, now)) {
				removeQueued(candidate);
				candidate.state = State.TERMINAL;
				expired.add(candidate);
				continue;
			}
			AcquireResult rate = rateLimiter.tryAcquire();
			if (!rate.allowed()) {
				markRateDelayed(candidate);
				scheduleRateRetry(rate.retryAfter());
				return;
			}
			removeQueued(candidate);
			candidate.state = State.STARTING;
			promoted.add(candidate);
		}
		cancelRateRetry();
	}

	private void rateRetry() {
		List<QueueEntry> expired = new ArrayList<>();
		List<QueueEntry> promoted = new ArrayList<>();
		synchronized (lock) {
			rateRetryHandle = null;
			if (accepting) {
				drainQueue(expired, promoted);
			}
		}
		startPromoted(promoted);
		completeExpired(expired);
	}

	private boolean cancel(QueueEntry entry) {
		AnswerAssessmentTask activeTask = null;
		boolean cancelledWhileQueued = false;
		List<QueueEntry> expired = new ArrayList<>();
		List<QueueEntry> promoted = new ArrayList<>();
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
					if (accepting) {
						drainQueue(expired, promoted);
					}
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
			boolean cancelled = entry.result.cancel(false);
			startPromoted(promoted);
			completeExpired(expired);
			return cancelled;
		}
		return activeTask != null && activeTask.cancel();
	}

	private void timeout(QueueEntry entry) {
		List<QueueEntry> expired = new ArrayList<>();
		List<QueueEntry> promoted = new ArrayList<>();
		synchronized (lock) {
			if (entry.state != State.QUEUED || !queue.remove(entry)) {
				return;
			}
			queuedRequests.decrementAndGet();
			entry.state = State.TERMINAL;
			if (accepting) {
				drainQueue(expired, promoted);
			}
		}
		completeTimeout(entry);
		startPromoted(promoted);
		completeExpired(expired);
	}

	private void startPromoted(List<QueueEntry> entries) {
		for (QueueEntry entry : entries) {
			metrics.recordPromoted();
			recordWait(entry, QueueWaitResult.PROMOTED);
			start(entry);
		}
	}

	private void completeExpired(List<QueueEntry> entries) {
		for (QueueEntry entry : entries) {
			completeTimeout(entry);
		}
	}

	private void completeTimeout(QueueEntry entry) {
		metrics.recordQueueTimeout();
		metrics.recordRejected();
		if (entry.rateDelayed) {
			rateLimiter.recordRejected();
		}
		recordWait(entry, QueueWaitResult.TIMEOUT);
		entry.result.completeExceptionally(overloadedException());
	}

	private void failScheduling(QueueEntry entry) {
		entry.result.completeExceptionally(new IllegalStateException(
			"답안 평가 대기열 timeout을 예약할 수 없습니다.",
			entry.schedulingFailure));
	}

	private void rejectFull(QueueEntry entry) {
		metrics.recordQueueFull();
		metrics.recordRejected();
		entry.state = State.TERMINAL;
		entry.result.completeExceptionally(overloadedException());
	}

	private void rejectRate(QueueEntry entry) {
		rateLimiter.recordRejected();
		metrics.recordQueueFull();
		metrics.recordRejected();
		entry.state = State.TERMINAL;
		entry.result.completeExceptionally(overloadedException());
	}

	private void markRateDelayed(QueueEntry entry) {
		if (!entry.rateDelayed) {
			entry.rateDelayed = true;
			rateLimiter.recordDelayed();
		}
	}

	private void scheduleRateRetry(Duration retryAfter) {
		if (rateRetryHandle != null) {
			return;
		}
		Duration delay = retryAfter.isZero() || retryAfter.isNegative()
			? Duration.ofNanos(1) : retryAfter;
		rateRetryHandle = timeoutScheduler.schedule(this::rateRetry, delay);
	}

	private void cancelRateRetry() {
		if (rateRetryHandle != null) {
			rateRetryHandle.cancel();
			rateRetryHandle = null;
		}
	}

	private void removeQueued(QueueEntry entry) {
		queue.remove(entry);
		queuedRequests.decrementAndGet();
		cancelTimeout(entry);
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

	private AnswerAssessmentOverloadedException overloadedException() {
		return new AnswerAssessmentOverloadedException();
	}

	private IllegalStateException shutdownException() {
		return new IllegalStateException("답안 평가 rate limit 대기열이 종료되었습니다.");
	}

	private enum Admission {
		START, QUEUE, FULL, RATE_REJECTED, SHUTDOWN, SCHEDULER_FAILURE
	}

	private enum State {
		QUEUED, STARTING, ACTIVE, TERMINAL
	}

	private final class QueueEntry {

		private final Supplier<AnswerAssessmentTask> taskSupplier;
		private final CompletableFuture<AnswerAssessment> result =
			new CompletableFuture<>();
		private final AnswerAssessmentTask task = new AnswerAssessmentTask(
			result, () -> cancel(this));
		private final long enqueuedAtNanos;
		private State state = State.QUEUED;
		private AnswerAssessmentQueueTimeoutScheduler.TimeoutHandle timeoutHandle;
		private AnswerAssessmentTask activeTask;
		private RuntimeException schedulingFailure;
		private boolean cancelRequested;
		private boolean rateDelayed;

		private QueueEntry(
			Supplier<AnswerAssessmentTask> taskSupplier,
			long enqueuedAtNanos
		) {
			this.taskSupplier = taskSupplier;
			this.enqueuedAtNanos = enqueuedAtNanos;
		}
	}
}
