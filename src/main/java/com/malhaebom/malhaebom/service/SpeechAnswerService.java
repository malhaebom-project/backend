package com.malhaebom.malhaebom.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerRequest;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerStartResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerTask;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;
import com.malhaebom.malhaebom.service.port.SpeechAnswerLifecycleOperations;
import com.malhaebom.malhaebom.service.port.SpeechTranscriptionRateLimit;
import com.malhaebom.malhaebom.service.policy.SpeechShutdownPolicy;
import com.malhaebom.malhaebom.service.policy.SpeechTranscriptionConcurrencyPolicy;
import com.malhaebom.malhaebom.service.policy.SpeechTranscriptionConcurrencyPolicy.Permit;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SpeechAnswerService implements SpeechAnswerLifecycleOperations {
	private static final long MAX_SHUTDOWN_CLEANUP_MILLIS = 5_000L;
	private static final long MIN_SHUTDOWN_CLEANUP_MILLIS = 100L;

	private final SpeechAnswerStateService stateService;
	private final SpeechTranscriber transcriber;
	private final Executor completionExecutor;
	private final SpeechTranscriptionConcurrencyPolicy concurrencyPolicy;
	private final SpeechTranscriptionRateLimit rateLimit;
	private final SpeechShutdownPolicy shutdownPolicy;
	private final InFlightSpeechAnswerRegistry inFlightRegistry;
	private final ReentrantReadWriteLock lifecycleLock =
		new ReentrantReadWriteLock();
	private final CompletableFuture<Void> shutdownCompletion =
		new CompletableFuture<>();
	private volatile boolean running = true;

	@Autowired
	public SpeechAnswerService(
		SpeechAnswerStateService stateService,
		SpeechTranscriber transcriber,
		@Qualifier("speechCompletionExecutor")
		Executor completionExecutor,
		SpeechTranscriptionConcurrencyPolicy concurrencyPolicy,
		SpeechTranscriptionRateLimit rateLimit,
		SpeechShutdownPolicy shutdownPolicy,
		InFlightSpeechAnswerRegistry inFlightRegistry
	) {
		this.stateService = stateService;
		this.transcriber = transcriber;
		this.completionExecutor = completionExecutor;
		this.concurrencyPolicy = concurrencyPolicy;
		this.rateLimit = rateLimit;
		this.shutdownPolicy = shutdownPolicy;
		this.inFlightRegistry = inFlightRegistry;
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public SpeechAnswerTask uploadAsync(SpeechAnswerRequest request) {
		Objects.requireNonNull(request, "음성 답변 요청은 null일 수 없습니다.");
		Lock lifecycleReadLock = lifecycleLock.readLock();
		lifecycleReadLock.lock();
		try {
			validateAcceptingRequests();
			return uploadWhileRunning(
				request
			);
		} finally {
			lifecycleReadLock.unlock();
		}
	}

	private SpeechAnswerTask uploadWhileRunning(SpeechAnswerRequest request) {
		long startedAt = System.nanoTime();
		return inFlightRegistry.withRequestLock(request.requestKey(), () -> {
			SpeechAnswerStartResult startResult = stateService.start(
				request.userId(),
				request.sessionId(),
				request.sessionQuestionId(),
				request.requestKey()
			);
			if (startResult.isCompleted()) {
				return completed(startResult.speechAnswer(), startedAt);
			}
			if (startResult.isProcessing()) {
				return inFlightRegistry.join(
					startResult,
					request.requestKey()
				);
			}

			SpeechAnswerTask sharedTask = startClaimed(
				startResult,
				request.audio(),
				startedAt
			);
			return inFlightRegistry.register(
				request.requestKey(),
				startResult,
				sharedTask
			);
		});
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

	private SpeechAnswerTask startClaimed(
		SpeechAnswerStartResult startResult,
		SpeechAudio audio,
		long startedAt
	) {
		SpeechAnswer started = startResult.speechAnswer();
		String provider = transcriber.provider();
		Permit permit = concurrencyPolicy.tryAcquire();
		if (permit == null) {
			return reject(
				started.getId(),
				startResult.processingToken(),
				provider,
				ErrorCode.STT_PROCESSING_OVERLOADED,
				startedAt
			);
		}
		if (!rateLimit.tryAcquire()) {
			permit.release();
			return reject(
				started.getId(),
				startResult.processingToken(),
				provider,
				ErrorCode.AI_REQUEST_LIMIT_EXCEEDED,
				startedAt
			);
		}
		log.info(
			"event=stt_accepted active={} limit={}",
			concurrencyPolicy.activeRequests(),
			concurrencyPolicy.maxConcurrentRequests()
		);

		SpeechAnswerTask task;
		try {
			task = transcribeAndComplete(startResult, audio, provider);
		} catch (RuntimeException exception) {
			permit.release();
			failClaimed(startResult, provider, exception);
			logFailure(startedAt, exception);
			throw exception;
		}
		task.result().whenComplete((result, exception) -> {
			permit.release();
			if (exception == null) {
				log.info(
					"event=stt_completed cached=false duration_ms={} active={} limit={}",
					elapsedMillis(startedAt),
					concurrencyPolicy.activeRequests(),
					concurrencyPolicy.maxConcurrentRequests()
				);
				return;
			}
			logFailure(startedAt, exception);
		});
		return task;
	}

	private SpeechAnswerTask completed(
		SpeechAnswer speechAnswer,
		long startedAt
	) {
		log.info(
			"event=stt_completed cached=true duration_ms={} active={} limit={}",
			elapsedMillis(startedAt),
			concurrencyPolicy.activeRequests(),
			concurrencyPolicy.maxConcurrentRequests()
		);
		return SpeechAnswerTask.completed(SpeechAnswerResult.from(speechAnswer));
	}

	private SpeechAnswerTask reject(
		Long speechAnswerId,
		String processingToken,
		String provider,
		ErrorCode errorCode,
		long startedAt
	) {
		ApiException overload = new ApiException(errorCode);
		CompletableFuture<SpeechAnswerResult> result = new CompletableFuture<>();
		fail(
			speechAnswerId,
			processingToken,
			provider,
			overload,
			result
		);
		log.warn(
			"event=stt_rejected error_code={} duration_ms={} active={} limit={}",
			overload.getErrorCode(),
			elapsedMillis(startedAt),
			concurrencyPolicy.activeRequests(),
			concurrencyPolicy.maxConcurrentRequests()
		);
		return new SpeechAnswerTask(result, () -> false);
	}

	private SpeechAnswerTask transcribeAndComplete(
		SpeechAnswerStartResult startResult,
		SpeechAudio audio,
		String provider
	) {
		CompletableFuture<SpeechAnswerResult> result = new CompletableFuture<>();
		AtomicBoolean terminal = new AtomicBoolean();
		SpeechTranscriptionTask transcription = Objects.requireNonNull(
			transcriber.transcribeAsync(
				audio,
				startResult.adaptationPhrases()
			),
			"음성 변환 작업은 null일 수 없습니다."
		);

		transcription.result().whenComplete((transcriptionResult, exception) ->
			executeCompletion(() -> completeFromProvider(
				startResult.speechAnswer().getId(),
				startResult.processingToken(),
				provider,
				transcriptionResult,
				exception,
				terminal,
				result
			))
		);
		return new SpeechAnswerTask(
			result,
			() -> cancel(
				startResult.speechAnswer().getId(),
				startResult.processingToken(),
				provider,
				transcription,
				terminal,
				result
			)
		);
	}

	private void failClaimed(
		SpeechAnswerStartResult startResult,
		String provider,
		RuntimeException exception
	) {
		try {
			stateService.fail(
				startResult.speechAnswer().getId(),
				startResult.processingToken(),
				ErrorCode.STT_PROCESSING_FAILED.getMessage(),
				provider
			);
		} catch (RuntimeException stateFailure) {
			exception.addSuppressed(stateFailure);
		}
	}

	private void completeFromProvider(
		Long speechAnswerId,
		String processingToken,
		String provider,
		SpeechTranscriptionResult transcription,
		Throwable exception,
		AtomicBoolean terminal,
		CompletableFuture<SpeechAnswerResult> result
	) {
		if (!terminal.compareAndSet(false, true)) {
			return;
		}
		if (exception != null) {
			fail(
				speechAnswerId,
				processingToken,
				provider,
				exception,
				result
			);
			return;
		}

		try {
			validateTranscript(transcription);
			SpeechAnswer completed = stateService.complete(
				speechAnswerId,
				processingToken,
				transcription.transcript(),
				transcription.confidence(),
				provider
			).orElseThrow(() -> new ApiException(
				ErrorCode.SPEECH_PROCESSING,
				"음성 답변 처리 권한이 만료되었습니다."
			));
			result.complete(SpeechAnswerResult.from(completed));
		} catch (RuntimeException failure) {
			fail(
				speechAnswerId,
				processingToken,
				provider,
				failure,
				result
			);
		}
	}

	private boolean cancel(
		Long speechAnswerId,
		String processingToken,
		String provider,
		SpeechTranscriptionTask transcription,
		AtomicBoolean terminal,
		CompletableFuture<SpeechAnswerResult> result
	) {
		if (!terminal.compareAndSet(false, true)) {
			return false;
		}

		ApiException timeout = new ApiException(
			ErrorCode.STT_PROCESSING_TIMEOUT
		);
		try {
			transcription.cancel();
		} catch (RuntimeException exception) {
			timeout.addSuppressed(exception);
		}
		executeCompletion(() -> fail(
			speechAnswerId,
			processingToken,
			provider,
			timeout,
			result
		));
		return true;
	}

	private void fail(
		Long speechAnswerId,
		String processingToken,
		String provider,
		Throwable failure,
		CompletableFuture<SpeechAnswerResult> result
	) {
		try {
			stateService.fail(
				speechAnswerId,
				processingToken,
				errorCode(failure).getMessage(),
				provider
			);
		} catch (RuntimeException stateFailure) {
			failure.addSuppressed(stateFailure);
		}
		result.completeExceptionally(failure);
	}

	private void executeCompletion(Runnable completion) {
		try {
			completionExecutor.execute(completion);
		} catch (RejectedExecutionException exception) {
			completion.run();
		}
	}

	private void logFailure(long startedAt, Throwable exception) {
		log.warn(
			"event=stt_failed error_code={} duration_ms={} active={} limit={}",
			errorCode(exception),
			elapsedMillis(startedAt),
			concurrencyPolicy.activeRequests(),
			concurrencyPolicy.maxConcurrentRequests()
		);
	}

	private ErrorCode errorCode(Throwable failure) {
		if (failure instanceof ApiException apiException) {
			return apiException.getErrorCode();
		}
		return ErrorCode.STT_PROCESSING_FAILED;
	}

	private long elapsedMillis(long startedAt) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
	}

	private void validateTranscript(SpeechTranscriptionResult result) {
		if (result == null
			|| result.transcript() == null
			|| result.transcript().isBlank()) {
			throw new ApiException(ErrorCode.SPEECH_NOT_RECOGNIZED);
		}
	}

	@Override
	public void start() {
		running = true;
	}

	@Override
	public void stop() {
		stop(() -> {
		});
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
		long drainTimeoutMillis = shutdownPolicy.drainTimeout()
			.toMillis();
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

	private void awaitCancellation(
		List<SpeechAnswerTask> pending
	) {
		if (pending.isEmpty()) {
			completeShutdown(true, 0);
			return;
		}
		long cleanupMillis = Math.min(
			MAX_SHUTDOWN_CLEANUP_MILLIS,
			Math.max(
				MIN_SHUTDOWN_CLEANUP_MILLIS,
				shutdownPolicy.drainTimeout().toMillis() / 4
			)
		);
		allTasks(pending)
			.completeOnTimeout(null, cleanupMillis, TimeUnit.MILLISECONDS)
			.whenComplete((ignored, exception) ->
				completeShutdown(true, countPending(pending))
			);
	}

	private CompletableFuture<Void> allTasks(
		List<SpeechAnswerTask> tasks
	) {
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
