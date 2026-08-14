package com.malhaebom.malhaebom.service;

import java.util.Objects;

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
	public AnswerSubmissionResult submit(
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
		AnswerSubmissionPreparation preparation = submissionTransactionService
			.prepare(sessionId, sessionQuestionId, speechAnswerId);
		return switch (preparation) {
			case Completed completed -> completed.result();
			case Processing processing -> assessAndComplete(processing);
		};
	}

	private AnswerSubmissionResult assessAndComplete(Processing processing) {
		AnswerAssessment assessment = assess(processing);
		return complete(processing, assessment);
	}

	private AnswerAssessment assess(Processing processing) {
		AnswerAssessment assessment;
		try {
			assessment = answerAssessmentService.assess(
				processing.assessmentInput()
			);
		} catch (RuntimeException exception) {
			fail(processing, exception);
			throw new ApiException(
				ErrorCode.ANSWER_ASSESSMENT_FAILED,
				exception
			);
		}
		return assessment;
	}

	private AnswerSubmissionResult complete(
		Processing processing,
		AnswerAssessment assessment
	) {
		try {
			return submissionTransactionService.complete(
				processing.submissionId(),
				processing.processingToken(),
				assessment
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
