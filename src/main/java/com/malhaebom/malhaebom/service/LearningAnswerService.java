package com.malhaebom.malhaebom.service;

import com.malhaebom.malhaebom.global.concurrent.CompletionFailures;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.*;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation.Completed;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation.Processing;
import com.malhaebom.malhaebom.service.exception.AnswerAssessmentOverloadedException;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class LearningAnswerService {
	private final AnswerAssessmentGenerator answerAssessmentGenerator;
	private final AnswerSubmissionTransactionService submissionTransactionService;
	private final Clock clock;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public AnswerSubmissionTask submitAsync(Long userId, Long sessionId, Long sessionQuestionId, Long speechAnswerId) {
		AnswerSubmissionPreparation preparation = submissionTransactionService
			.prepare(userId, sessionId, sessionQuestionId, speechAnswerId);
		return switch (preparation) {
			case Completed completed -> AnswerSubmissionTask.completed(completed.result());
			case Processing processing -> assessAndComplete(processing);
		};
	}

	private AnswerSubmissionTask assessAndComplete(Processing processing) {
		AnswerAssessmentTask task = assessAsync(processing);

		AtomicReference<ApiException> cancellation = new AtomicReference<>();
		CompletionStage<AnswerSubmissionResult> result = withinDeadline(processing, task)
			.exceptionallyCompose(exception -> handleAssessmentFailure(
				processing,
				task,
				cancellation,
				exception
			))
			.thenApply(assessment -> completeUnlessCancelled(
				processing,
				assessment,
				cancellation
			));
		return new AnswerSubmissionTask(
			result,
			() -> cancel(processing, task, cancellation)
		);
	}

	private AnswerAssessmentTask assessAsync(Processing processing) {
		AnswerAssessmentTask task = Objects.requireNonNull(
			answerAssessmentGenerator.generateAsync(
				processing.assessmentInput()
			),
			"AI 평가 작업은 null일 수 없습니다."
		);
		return task.map(result -> {
			if (result == null) {
				throw new IllegalStateException("AI 평가 결과가 비어 있습니다.");
			}
			return result;
		});
	}

	private CompletionStage<AnswerAssessment> withinDeadline(
		Processing processing,
		AnswerAssessmentTask task
	) {
		Duration remaining = processing.deadline().remainingAt(clock.instant());
		if (remaining.isZero()) {
			return CompletableFuture.failedFuture(new TimeoutException());
		}

		CompletableFuture<AnswerAssessment> result = new CompletableFuture<>();
		task.result().whenComplete((assessment, exception) -> {
			if (exception != null) {
				result.completeExceptionally(exception);
				return;
			}
			result.complete(assessment);
		});
		return result.orTimeout(
			remaining.toNanos(),
			TimeUnit.NANOSECONDS
		);
	}

	private CompletionStage<AnswerAssessment> handleAssessmentFailure(
		Processing processing,
		AnswerAssessmentTask task,
		AtomicReference<ApiException> cancellation,
		Throwable exception
	) {
		ApiException cancellationException = cancellation.get();
		if (cancellationException != null) {
			return CompletableFuture.failedFuture(cancellationException);
		}
		Throwable cause = CompletionFailures.unwrap(exception);
		if (cause instanceof TimeoutException) {
			return timeout(processing, task);
		}
		if (cause instanceof AnswerAssessmentOverloadedException) {
			return failAssessment(
				processing,
				cause,
				ErrorCode.ANSWER_ASSESSMENT_OVERLOADED
			);
		}
		return failAssessment(
			processing,
			cause,
			ErrorCode.ANSWER_ASSESSMENT_FAILED
		);
	}

	private CompletionStage<AnswerAssessment> failAssessment(Processing processing, Throwable cause, ErrorCode errorCode) {
		ApiException exception = new ApiException(errorCode, cause);
		fail(processing, cause);
		return CompletableFuture.failedFuture(exception);
	}

	private CompletionStage<AnswerAssessment> timeout(Processing processing, AnswerAssessmentTask task) {
		ApiException timeout = new ApiException(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT
		);
		try {
			task.cancel();
		} catch (RuntimeException exception) {
			timeout.addSuppressed(exception);
		}
		fail(processing, timeout);
		return CompletableFuture.failedFuture(timeout);
	}

	private boolean cancel(Processing processing, AnswerAssessmentTask task, AtomicReference<ApiException> cancellation) {
		ApiException timeout = new ApiException(
			ErrorCode.ANSWER_SUBMISSION_TIMEOUT
		);
		if (!cancellation.compareAndSet(null, timeout)) {
			return false;
		}

		try {
			task.cancel();
		} catch (RuntimeException exception) {
			timeout.addSuppressed(exception);
		}
		try {
			fail(processing, timeout);
		} catch (RuntimeException exception) {
			timeout.addSuppressed(exception);
		}
		return true;
	}

	private AnswerSubmissionResult complete(Processing processing, AnswerAssessment assessment) {
		try {
			return submissionTransactionService.complete(
				processing.submissionId(),
				processing.processingToken(),
				assessment,
				processing.deadline()
			);
		} catch (RuntimeException exception) {
			fail(processing, exception);
			throw exception;
		}
	}

	private AnswerSubmissionResult completeUnlessCancelled(
		Processing processing,
		AnswerAssessment assessment,
		AtomicReference<ApiException> cancellation
	) {
		ApiException cancellationException = cancellation.get();
		if (cancellationException != null) {
			throw cancellationException;
		}
		return complete(processing, assessment);
	}

	private void fail(Processing processing, Throwable exception) {
		submissionTransactionService.fail(
			processing.submissionId(),
			processing.processingToken(),
			exception
		);
	}
}
