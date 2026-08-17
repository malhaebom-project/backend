package com.malhaebom.malhaebom.service;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

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
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation.Completed;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation.Processing;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningAnswerService {

	private final LearningSessionRepository learningSessionRepository;
	private final AnswerRepository answerRepository;
	private final AnswerSubmissionRepository answerSubmissionRepository;
	private final AnswerAssessmentService answerAssessmentService;
	private final AnswerSubmissionTransactionService submissionTransactionService;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public CompletionStage<AnswerSubmissionResult> submitAsync(
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
		AnswerSubmissionPreparation preparation = submissionTransactionService
			.prepare(sessionId, sessionQuestionId, speechAnswerId);
		return switch (preparation) {
			case Completed completed -> CompletableFuture.completedFuture(
				completed.result()
			);
			case Processing processing -> assessAndComplete(processing);
		};
	}

	private CompletionStage<AnswerSubmissionResult> assessAndComplete(
		Processing processing
	) {
		return assess(processing)
			.thenApply(assessment -> complete(processing, assessment));
	}

	private CompletionStage<AnswerAssessment> assess(Processing processing) {
		CompletionStage<AnswerAssessment> assessment;
		try {
			assessment = answerAssessmentService
				.assessAsync(processing.assessmentInput());
		} catch (RuntimeException exception) {
			return failAssessment(processing, exception);
		}
		return assessment.exceptionallyCompose(exception ->
			failAssessment(processing, exception)
		);
	}

	private RuntimeException unwrapCompletionException(
		Throwable exception
	) {
		Throwable cause = exception;
		while (cause instanceof CompletionException
			&& cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause instanceof RuntimeException runtimeException
			? runtimeException
			: new RuntimeException(cause);
	}

	private CompletionStage<AnswerAssessment> failAssessment(
		Processing processing,
		Throwable exception
	) {
		RuntimeException cause = unwrapCompletionException(exception);
		fail(processing, cause);
		return CompletableFuture.failedFuture(assessmentException(cause));
	}

	private RuntimeException assessmentException(RuntimeException cause) {
		if (cause instanceof ApiException apiException
			&& apiException.getErrorCode()
				== ErrorCode.ANSWER_ASSESSMENT_OVERLOADED) {
			return apiException;
		}
		return new ApiException(ErrorCode.ANSWER_ASSESSMENT_FAILED, cause);
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

	private void fail(Processing processing, RuntimeException exception) {
		submissionTransactionService.fail(
			processing.submissionId(),
			processing.processingToken(),
			exception
		);
	}

	@Transactional
	public void skipRetry(Long sessionId, Long sessionQuestionId) {
		LearningSession session = learningSessionRepository
			.findForUpdateById(sessionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			));
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
