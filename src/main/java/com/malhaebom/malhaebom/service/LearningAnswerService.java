package com.malhaebom.malhaebom.service;

import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmissionStatus;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerSubmissionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningAnswerService {

	private static final int MAX_ATTEMPT_COUNT = 2;
	private static final Set<AnswerSubmissionStatus> BLOCKING_SUBMISSION_STATUSES =
		Set.of(
			AnswerSubmissionStatus.PENDING,
			AnswerSubmissionStatus.PROCESSING,
			AnswerSubmissionStatus.FAILED
		);

	private final LearningSessionRepository learningSessionRepository;
	private final AnswerRepository answerRepository;
	private final AnswerSubmissionRepository answerSubmissionRepository;
	private final AnswerAssessmentService answerAssessmentService;
	private final AnswerSubmissionTransactionService submissionTransactionService;

	public AnswerSubmissionResult submit(
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
		AnswerSubmissionPreparation preparation = submissionTransactionService
			.prepare(sessionId, sessionQuestionId, speechAnswerId);
		if (!preparation.requiresAssessment()) {
			return preparation.completedResult();
		}

		AnswerAssessment assessment;
		try {
			assessment = answerAssessmentService.assess(
				preparation.assessmentInput()
			);
		} catch (RuntimeException exception) {
			submissionTransactionService.fail(
				preparation.submissionId(),
				preparation.processingToken(),
				exception
			);
			throw new ApiException(
				ErrorCode.ANSWER_ASSESSMENT_FAILED,
				exception
			);
		}

		try {
			return submissionTransactionService.complete(
				preparation.submissionId(),
				preparation.processingToken(),
				assessment
			);
		} catch (RuntimeException exception) {
			submissionTransactionService.fail(
				preparation.submissionId(),
				preparation.processingToken(),
				exception
			);
			throw exception;
		}
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
		if (latestAnswer.isCorrect()
			|| latestAnswer.getAttemptNo() >= MAX_ATTEMPT_COUNT) {
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
			.existsBySessionQuestion_IdAndStatusIn(
				sessionQuestionId,
				BLOCKING_SUBMISSION_STATUSES
			)) {
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
