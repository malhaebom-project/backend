package com.malhaebom.malhaebom.service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.async.AsyncConfiguration;
import com.malhaebom.malhaebom.infra.speech.SpeechTranscriptionConcurrencyLimiter;
import com.malhaebom.malhaebom.infra.speech.SpeechTranscriptionConcurrencyLimiter.Permit;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerStartResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerTask;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SpeechAnswerService {
	private static final int REQUEST_LOCK_COUNT = 64;

	private final SpeechAnswerStateService stateService;
	private final SpeechTranscriber transcriber;
	private final Executor completionExecutor;
	private final SpeechTranscriptionConcurrencyLimiter concurrencyLimiter;
	private final ConcurrentMap<String, InFlightSpeechAnswerTask> inFlightTasks =
		new ConcurrentHashMap<>();
	private final ReentrantLock[] requestLocks = createRequestLocks();

	public SpeechAnswerService(
		SpeechAnswerStateService stateService,
		SpeechTranscriber transcriber,
		@Qualifier(AsyncConfiguration.SPEECH_COMPLETION_EXECUTOR)
		Executor completionExecutor,
		SpeechTranscriptionConcurrencyLimiter concurrencyLimiter
	) {
		this.stateService = stateService;
		this.transcriber = transcriber;
		this.completionExecutor = completionExecutor;
		this.concurrencyLimiter = concurrencyLimiter;
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public SpeechAnswerTask uploadAsync(
		Long sessionId,
		Long sessionQuestionId,
		String requestKey,
		SpeechAudio audio
	) {
		Objects.requireNonNull(audio, "음성 파일은 null일 수 없습니다.");
		long startedAt = System.nanoTime();
		ReentrantLock requestLock = requestLock(requestKey);
		requestLock.lock();
		try {
			SpeechAnswerStartResult startResult = stateService.start(
				sessionId,
				sessionQuestionId,
				requestKey
			);
			if (startResult.isCompleted()) {
				return completed(startResult.speechAnswer(), startedAt);
			}
			if (startResult.isProcessing()) {
				return join(startResult, requestKey);
			}

			SpeechAnswerTask sharedTask = startClaimed(
				startResult,
				audio,
				startedAt
			);
			return register(requestKey, startResult, sharedTask);
		} finally {
			requestLock.unlock();
		}
	}

	private SpeechAnswerTask startClaimed(
		SpeechAnswerStartResult startResult,
		SpeechAudio audio,
		long startedAt
	) {
		SpeechAnswer started = startResult.speechAnswer();
		String provider = transcriber.provider();
		Permit permit = concurrencyLimiter.tryAcquire();
		if (permit == null) {
			return reject(
				started.getId(),
				startResult.processingToken(),
				provider,
				startedAt
			);
		}
		log.info(
			"event=stt_accepted active={} limit={}",
			concurrencyLimiter.activeRequests(),
			concurrencyLimiter.maxConcurrentRequests()
		);

		SpeechAnswerTask task;
		try {
			task = transcribeAndComplete(startResult, audio, provider);
		} catch (RuntimeException exception) {
			permit.release();
			logFailure(startedAt, exception);
			throw exception;
		}
		task.result().whenComplete((result, exception) -> {
			permit.release();
			if (exception == null) {
				log.info(
					"event=stt_completed cached=false duration_ms={} active={} limit={}",
					elapsedMillis(startedAt),
					concurrencyLimiter.activeRequests(),
					concurrencyLimiter.maxConcurrentRequests()
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
			concurrencyLimiter.activeRequests(),
			concurrencyLimiter.maxConcurrentRequests()
		);
		return SpeechAnswerTask.completed(SpeechAnswerResult.from(speechAnswer));
	}

	private SpeechAnswerTask join(
		SpeechAnswerStartResult startResult,
		String requestKey
	) {
		InFlightSpeechAnswerTask inFlight = inFlightTasks.get(requestKey);
		if (inFlight == null || !inFlight.matches(
			startResult.speechAnswer().getId(),
			startResult.processingToken()
		)) {
			throw new ApiException(ErrorCode.SPEECH_PROCESSING);
		}

		log.info(
			"event=stt_rejoined speech_answer_id={} subscribers={}",
			startResult.speechAnswer().getId(),
			inFlight.subscriberCount() + 1
		);
		return inFlight.subscribe();
	}

	private SpeechAnswerTask register(
		String requestKey,
		SpeechAnswerStartResult startResult,
		SpeechAnswerTask sharedTask
	) {
		InFlightSpeechAnswerTask inFlight = new InFlightSpeechAnswerTask(
			startResult.speechAnswer().getId(),
			startResult.processingToken(),
			sharedTask
		);
		InFlightSpeechAnswerTask previous = inFlightTasks.put(
			requestKey,
			inFlight
		);
		sharedTask.result().whenComplete((result, exception) ->
			inFlightTasks.remove(requestKey, inFlight)
		);
		if (previous != null) {
			previous.expire();
		}
		return inFlight.subscribe();
	}

	private SpeechAnswerTask reject(
		Long speechAnswerId,
		String processingToken,
		String provider,
		long startedAt
	) {
		ApiException overload = new ApiException(
			ErrorCode.STT_PROCESSING_OVERLOADED
		);
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
			concurrencyLimiter.activeRequests(),
			concurrencyLimiter.maxConcurrentRequests()
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
		SpeechTranscriptionTask transcription;
		try {
			transcription = Objects.requireNonNull(
				transcriber.transcribeAsync(
					audio,
					startResult.adaptationPhrases()
				),
				"음성 변환 작업은 null일 수 없습니다."
			);
		} catch (RuntimeException exception) {
			terminal.set(true);
			executeCompletion(() -> fail(
				startResult.speechAnswer().getId(),
				startResult.processingToken(),
				provider,
				toApiException(exception),
				result
			));
			return new SpeechAnswerTask(result, () -> false);
		}

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
				toApiException(exception),
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
				toApiException(failure),
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
		ApiException failure,
		CompletableFuture<SpeechAnswerResult> result
	) {
		try {
			stateService.fail(
				speechAnswerId,
				processingToken,
				failure.getErrorCode().getMessage(),
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

	private ApiException toApiException(Throwable exception) {
		Throwable cause = unwrapCompletionException(exception);
		if (cause instanceof ApiException apiException) {
			return apiException;
		}
		return new ApiException(ErrorCode.STT_PROCESSING_FAILED, cause);
	}

	private Throwable unwrapCompletionException(Throwable exception) {
		Throwable cause = exception;
		while ((cause instanceof CompletionException
			|| cause instanceof ExecutionException)
			&& cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}

	private void logFailure(long startedAt, Throwable exception) {
		Throwable cause = unwrapCompletionException(exception);
		ErrorCode errorCode = cause instanceof ApiException apiException
			? apiException.getErrorCode()
			: ErrorCode.STT_PROCESSING_FAILED;
		log.warn(
			"event=stt_failed error_code={} duration_ms={} active={} limit={}",
			errorCode,
			elapsedMillis(startedAt),
			concurrencyLimiter.activeRequests(),
			concurrencyLimiter.maxConcurrentRequests()
		);
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

	private ReentrantLock requestLock(String requestKey) {
		int hash = requestKey == null ? 0 : requestKey.hashCode();
		return requestLocks[(hash & Integer.MAX_VALUE) % requestLocks.length];
	}

	private static ReentrantLock[] createRequestLocks() {
		ReentrantLock[] locks = new ReentrantLock[REQUEST_LOCK_COUNT];
		for (int index = 0; index < locks.length; index++) {
			locks[index] = new ReentrantLock();
		}
		return locks;
	}

	private static final class InFlightSpeechAnswerTask {

		private final Long speechAnswerId;
		private final String processingToken;
		private final SpeechAnswerTask sharedTask;
		private final AtomicInteger subscribers = new AtomicInteger();

		private InFlightSpeechAnswerTask(
			Long speechAnswerId,
			String processingToken,
			SpeechAnswerTask sharedTask
		) {
			this.speechAnswerId = speechAnswerId;
			this.processingToken = processingToken;
			this.sharedTask = sharedTask;
		}

		private boolean matches(Long speechAnswerId, String processingToken) {
			return Objects.equals(this.speechAnswerId, speechAnswerId)
				&& Objects.equals(this.processingToken, processingToken);
		}

		private int subscriberCount() {
			return subscribers.get();
		}

		private SpeechAnswerTask subscribe() {
			CompletableFuture<SpeechAnswerResult> subscriberResult =
				new CompletableFuture<>();
			AtomicBoolean subscribed = new AtomicBoolean(true);
			subscribers.incrementAndGet();
			sharedTask.result().whenComplete((result, exception) -> {
				if (subscribed.compareAndSet(true, false)) {
					subscribers.decrementAndGet();
				}
				if (exception == null) {
					subscriberResult.complete(result);
				} else {
					subscriberResult.completeExceptionally(exception);
				}
			});
			return new SpeechAnswerTask(
				subscriberResult,
				() -> unsubscribe(subscribed)
			);
		}

		private boolean unsubscribe(AtomicBoolean subscribed) {
			if (!subscribed.compareAndSet(true, false)) {
				return false;
			}
			if (subscribers.decrementAndGet() == 0) {
				sharedTask.cancel();
			}
			return true;
		}

		private void expire() {
			sharedTask.cancel();
		}
	}
}
