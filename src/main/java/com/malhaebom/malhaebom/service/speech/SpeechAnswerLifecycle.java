package com.malhaebom.malhaebom.service.speech;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerTask;
import com.malhaebom.malhaebom.service.policy.SpeechShutdownPolicy;
import com.malhaebom.malhaebom.service.policy.SpeechTranscriptionConcurrencyPolicy;
import com.malhaebom.malhaebom.service.port.SpeechAnswerLifecycleOperations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpeechAnswerLifecycle implements SpeechAnswerLifecycleOperations {
	private static final long MAX_SHUTDOWN_CLEANUP_MILLIS = 5_000L;
	private static final long MIN_SHUTDOWN_CLEANUP_MILLIS = 100L;

	private final InFlightSpeechAnswerRegistry inFlightRegistry;
	private final SpeechTranscriptionConcurrencyPolicy concurrencyPolicy;
	private final SpeechShutdownPolicy shutdownPolicy;
	private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
	private final CompletableFuture<Void> shutdownCompletion = new CompletableFuture<>();
	private volatile boolean running = true;

	public <T> T whileAcceptingRequests(Supplier<T> operation) {
		Objects.requireNonNull(operation, "음성 답변 동작은 null일 수 없습니다.");
		Lock lifecycleReadLock = lifecycleLock.readLock();
		lifecycleReadLock.lock();
		try {
			validateAcceptingRequests();
			return operation.get();
		} finally {
			lifecycleReadLock.unlock();
		}
	}

	private void validateAcceptingRequests() {
		if (running) {
			return;
		}
		log.warn(
			"event=stt_rejected reason=shutdown active={} limit={}",
			concurrencyPolicy.activeRequests(),
			concurrencyPolicy.maxConcurrentRequests()
		);
		throw new ApiException(ErrorCode.STT_PROCESSING_OVERLOADED);
	}

	@Override
	public void start() {
		running = true;
	}

	@Override
	public void stop() {
		stop(() -> {});
	}

	@Override
	public void stop(Runnable callback) {
		Objects.requireNonNull(callback, "종료 완료 callback은 null일 수 없습니다.");
		shutdownCompletion.whenComplete((ignored, exception) -> callback.run());

		List<SpeechAnswerTask> snapshot;
		Lock lifecycleWriteLock = lifecycleLock.writeLock();
		lifecycleWriteLock.lock();
		try {
			if (!running) {
				return;
			}
			running = false;
			snapshot = inFlightRegistry.snapshot();
		} finally {
			lifecycleWriteLock.unlock();
		}
		drain(snapshot);
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private void drain(List<SpeechAnswerTask> snapshot) {
		if (snapshot.isEmpty()) {
			completeShutdown(false, 0);
			return;
		}

		CompletableFuture<Void> allTasks = allTasks(snapshot);
		AtomicBoolean drainFinished = new AtomicBoolean();
		allTasks.whenComplete((ignored, exception) -> {
			if (drainFinished.compareAndSet(false, true)) {
				completeShutdown(false, 0);
			}
		});
		long drainTimeoutMillis = shutdownPolicy.drainTimeout().toMillis();
		CompletableFuture.delayedExecutor(
			drainTimeoutMillis,
			TimeUnit.MILLISECONDS
		).execute(() -> {
			if (!drainFinished.compareAndSet(false, true)) {
				return;
			}
			List<SpeechAnswerTask> pending = snapshot.stream()
				.filter(task -> !isDone(task))
				.toList();
			log.warn(
				"event=stt_shutdown_timeout pending={} drain_timeout_ms={}",
				pending.size(),
				drainTimeoutMillis
			);
			pending.forEach(SpeechAnswerTask::cancel);
			awaitCancellation(pending);
		});
	}

	private void awaitCancellation(List<SpeechAnswerTask> pending) {
		if (pending.isEmpty()) {
			completeShutdown(true, 0);
			return;
		}
		long cleanupMillis = Math.clamp(
                shutdownPolicy.drainTimeout().toMillis() / 4,
                MIN_SHUTDOWN_CLEANUP_MILLIS,
                MAX_SHUTDOWN_CLEANUP_MILLIS
		);
		allTasks(pending)
			.completeOnTimeout(null, cleanupMillis, TimeUnit.MILLISECONDS)
			.whenComplete((ignored, exception) ->
				completeShutdown(true, countPending(pending))
			);
	}

	private CompletableFuture<Void> allTasks(List<SpeechAnswerTask> tasks) {
		CompletableFuture<?>[] results = tasks.stream()
			.map(task -> task.result().toCompletableFuture())
			.toArray(CompletableFuture[]::new);
		return CompletableFuture.allOf(results);
	}

	private int countPending(List<SpeechAnswerTask> tasks) {
		return (int)tasks.stream()
			.filter(task -> !isDone(task))
			.count();
	}

	private boolean isDone(SpeechAnswerTask task) {
		return task.result().toCompletableFuture().isDone();
	}

	private void completeShutdown(boolean timedOut, int pending) {
		if (!shutdownCompletion.complete(null)) {
			return;
		}
		log.info(
			"event=stt_shutdown_drained timed_out={} pending={}",
			timedOut,
			pending
		);
	}
}
