package com.malhaebom.malhaebom.service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.async.AsyncConfiguration;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerStartResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerTask;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionTask;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@Service
public class SpeechAnswerService {

	private final SpeechAnswerStateService stateService;
	private final SpeechTranscriber transcriber;
	private final Executor completionExecutor;

	public SpeechAnswerService(
		SpeechAnswerStateService stateService,
		SpeechTranscriber transcriber,
		@Qualifier(AsyncConfiguration.SPEECH_COMPLETION_EXECUTOR)
		Executor completionExecutor
	) {
		this.stateService = stateService;
		this.transcriber = transcriber;
		this.completionExecutor = completionExecutor;
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public SpeechAnswerTask uploadAsync(
		Long sessionId,
		Long sessionQuestionId,
		String requestKey,
		SpeechAudio audio
	) {
		Objects.requireNonNull(audio, "음성 파일은 null일 수 없습니다.");

		SpeechAnswerStartResult startResult = stateService.start(
			sessionId,
			sessionQuestionId,
			requestKey
		);
		SpeechAnswer started = startResult.speechAnswer();
		if (started.isCompleted()) {
			return SpeechAnswerTask.completed(SpeechAnswerResult.from(started));
		}

		String provider = transcriber.provider();
		return transcribeAndComplete(startResult, audio, provider);
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
				provider,
				toApiException(exception),
				result
			));
			return new SpeechAnswerTask(result, () -> false);
		}

		transcription.result().whenComplete((transcriptionResult, exception) ->
			executeCompletion(() -> completeFromProvider(
				startResult.speechAnswer().getId(),
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
				provider,
				transcription,
				terminal,
				result
			)
		);
	}

	private void completeFromProvider(
		Long speechAnswerId,
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
				transcription.transcript(),
				transcription.confidence(),
				provider
			);
			result.complete(SpeechAnswerResult.from(completed));
		} catch (RuntimeException failure) {
			fail(
				speechAnswerId,
				provider,
				toApiException(failure),
				result
			);
		}
	}

	private boolean cancel(
		Long speechAnswerId,
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
			provider,
			timeout,
			result
		));
		return true;
	}

	private void fail(
		Long speechAnswerId,
		String provider,
		ApiException failure,
		CompletableFuture<SpeechAnswerResult> result
	) {
		try {
			stateService.fail(
				speechAnswerId,
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

	private void validateTranscript(SpeechTranscriptionResult result) {
		if (result == null
			|| result.transcript() == null
			|| result.transcript().isBlank()) {
			throw new ApiException(ErrorCode.SPEECH_NOT_RECOGNIZED);
		}
	}
}
