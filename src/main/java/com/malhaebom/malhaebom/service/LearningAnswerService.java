package com.malhaebom.malhaebom.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerAttemptPolicy;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerSubmissionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation.Completed;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation.Processing;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionTask;
import com.malhaebom.malhaebom.service.exception.AnswerAssessmentOverloadedException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningAnswerService {

	private final LearningSessionRepository learningSessionRepository;
	private final AnswerRepository answerRepository;
	private final AnswerSubmissionRepository answerSubmissionRepository;
	private final AnswerAssessmentService answerAssessmentService;
	private final AnswerSubmissionTransactionService submissionTransactionService;
	private final ChildProfileService childProfileService;
	private final Clock clock;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public AnswerSubmissionTask submitAsync(
		Long userId,
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
		AnswerSubmissionPreparation preparation = submissionTransactionService
			.prepare(userId, sessionId, sessionQuestionId, speechAnswerId);
		return switch (preparation) {
			case Completed completed -> AnswerSubmissionTask.completed(
				completed.result()
			);
			case Processing processing -> assessAndComplete(processing);
		};
	}

	private AnswerSubmissionTask assessAndComplete(
		Processing processing
	) {
		AnswerAssessmentTask task = answerAssessmentService
			.assessAsync(processing.assessmentInput());

		AtomicReference<ApiException> cancellation = new AtomicReference<>();
		CompletionStage<AnswerSubmissionResult> result = withinDeadline(
			processing,
			task
		)
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
		Throwable cause = unwrapCompletionException(exception);
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

	private Throwable unwrapCompletionException(
		Throwable exception
	) {
		Throwable cause = exception;
		while (cause instanceof CompletionException
			&& cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}

	private CompletionStage<AnswerAssessment> failAssessment(
		Processing processing,
		Throwable cause,
		ErrorCode errorCode
	) {
		ApiException exception = new ApiException(errorCode, cause);
		fail(processing, cause);
		return CompletableFuture.failedFuture(exception);
	}

	private CompletionStage<AnswerAssessment> timeout(
		Processing processing,
		AnswerAssessmentTask task
	) {
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

	private boolean cancel(
		Processing processing,
		AnswerAssessmentTask task,
		AtomicReference<ApiException> cancellation
	) {
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

	private AnswerSubmissionResult complete(
		Processing processing,
		AnswerAssessment assessment
	) {
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

	@Transactional
	public void skipRetry(Long userId, Long sessionId, Long sessionQuestionId) {
		LearningSession session = learningSessionRepository
			.findForUpdateById(sessionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			));
		childProfileService.getOwnedActive(userId, session.getChildId());
		validateInProgress(session);
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, sessionQuestionId);
		validateNoConflictingSubmission(sessionQuestionId);

		Answer latestAnswer = answerRepository
			.findFirstBySessionQuestion_IdOrderByAttemptNoDesc(sessionQuestionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.INVALID_REQUEST,
				"오답 제출 후에만 재시도를 건너뛸 수 있습니다."
			));
		if (!AnswerAttemptPolicy.canRetry(latestAnswer)) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"재시도 가능한 오답이 아닙니다."
			);
		}

		session.skipRetryOnCurrentQuestion();
	}

	private void validateInProgress(LearningSession session) {
		if (!session.isInProgress()) {
			throw new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS
			);
		}
	}

	private void validateNoConflictingSubmission(Long sessionQuestionId) {
		if (answerSubmissionRepository
			.existsUnfinishedBySessionQuestionId(sessionQuestionId)) {
			throw new ApiException(ErrorCode.ANSWER_SUBMISSION_CONFLICT);
		}
	}

	private void validateCurrentQuestion(
		LearningSessionQuestion currentQuestion,
		Long sessionQuestionId
	) {
		if (!Objects.equals(currentQuestion.getId(), sessionQuestionId)) {
			throw new ApiException(ErrorCode.CURRENT_QUESTION_MISMATCH);
		}
	}
}
