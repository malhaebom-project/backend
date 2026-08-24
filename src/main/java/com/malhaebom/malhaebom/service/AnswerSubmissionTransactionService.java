package com.malhaebom.malhaebom.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerAttemptPolicy;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmission;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmissionStatus;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerSubmissionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionPreparation;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;
import com.malhaebom.malhaebom.service.port.AnswerSubmissionMetricsRecorder;
import com.malhaebom.malhaebom.service.policy.AnswerSubmissionDeadline;
import com.malhaebom.malhaebom.service.policy.AnswerSubmissionPolicyProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AnswerSubmissionPolicyProperties.class)
public class AnswerSubmissionTransactionService {

	private final LearningSessionRepository learningSessionRepository;
	private final SpeechAnswerRepository speechAnswerRepository;
	private final AnswerRepository answerRepository;
	private final AnswerSubmissionRepository answerSubmissionRepository;
	private final AnswerSubmissionPolicyProperties policyProperties;
	private final Clock clock;
	private final AnswerSubmissionMetricsRecorder metrics;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public AnswerSubmissionPreparation prepare(
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
		AnswerSubmissionDeadline deadline = AnswerSubmissionDeadline.startingAt(
			clock.instant(),
			policyProperties.processingTimeout()
		);
		answerSubmissionRepository
				.findBySpeechAnswer_Id(speechAnswerId)
				.ifPresent(existing ->
						validateRequestPath(existing, sessionId, sessionQuestionId)
				);

		LearningSession session = getSessionForUpdate(sessionId);
		AnswerSubmission lockedExisting = answerSubmissionRepository
			.findForUpdateBySpeechAnswer_Id(speechAnswerId)
			.orElse(null);
		if (lockedExisting != null) {
			return resolveExisting(
				lockedExisting,
				session,
				sessionQuestionId,
				clock.instant(),
				deadline
			);
		}

		validateInProgress(session);
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, sessionQuestionId);
		SpeechAnswer speechAnswer = getSpeechAnswer(speechAnswerId);
		validateSpeechAnswer(speechAnswer, currentQuestion);
		validateSpeechAnswerNotUsed(speechAnswerId);
		validateNoConflictingSubmission(currentQuestion.getId());

		int attemptNo = getNextAttemptNo(currentQuestion.getId());
		validateAttemptCount(attemptNo);
		AnswerSubmission submission = answerSubmissionRepository.save(
			AnswerSubmission.reserve(currentQuestion, speechAnswer, attemptNo)
		);
		AnswerSubmissionPreparation preparation = claim(
			submission,
			clock.instant(),
			deadline
		);
		metrics.recordNew();
		return preparation;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public AnswerSubmissionResult complete(
		Long submissionId,
		String processingToken,
		AnswerAssessment assessment,
		AnswerSubmissionDeadline deadline
	) {
		AnswerSubmission found = getSubmission(submissionId);
		LearningSession session = getSessionForUpdate(
			found.getSessionQuestion().getLearningSession().getId()
		);
		AnswerSubmission submission = getSubmissionForUpdate(submissionId);
		validateProcessingToken(submission, processingToken);
		validateDeadline(deadline);
		validateInProgress(session);
		validateCurrentQuestion(
			session.getCurrentQuestion(),
			submission.getSessionQuestion().getId()
		);

		Answer answer = answerRepository.save(Answer.create(
			submission,
			assessment.toEvaluation(),
			assessment.feedbackText()
		));
		boolean canRetry = AnswerAttemptPolicy.canRetry(answer);
		if (canRetry) {
			session.recordWrongAnswerAttempt();
		} else {
			session.completeCurrentQuestion(answer.isCorrect());
		}
		validateDeadline(deadline);
		submission.complete(processingToken, answer);

		return AnswerSubmissionResult.from(answer);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void fail(
		Long submissionId,
		String processingToken,
		RuntimeException exception
	) {
		AnswerSubmission submission = answerSubmissionRepository
			.findForUpdateById(submissionId)
			.orElse(null);
		if (submission == null
			|| !submission.isProcessingWithToken(processingToken)) {
			return;
		}

		submission.fail(
			processingToken,
			toFailureMessage(exception)
		);
	}

	private AnswerSubmissionPreparation resolveExisting(
		AnswerSubmission submission,
		LearningSession session,
		Long sessionQuestionId,
		Instant now,
		AnswerSubmissionDeadline deadline
	) {
		validateRequestPath(submission, session.getId(), sessionQuestionId);
		if (submission.isCompleted()) {
			metrics.recordCached();
			return AnswerSubmissionPreparation.completed(
				submission.getId(),
				AnswerSubmissionResult.from(submission.getAnswer())
			);
		}

		if (submission.isProcessing()
			&& !submission.isLeaseExpiredAt(now)) {
			metrics.recordProcessing();
			throw new ApiException(ErrorCode.ANSWER_SUBMISSION_PROCESSING);
		}

		validateInProgress(session);
		validateCurrentQuestion(
			session.getCurrentQuestion(),
			submission.getSessionQuestion().getId()
		);
		boolean retryingFailed =
			submission.getStatus() == AnswerSubmissionStatus.FAILED;
		if (retryingFailed) {
			submission.retry();
		}
		AnswerSubmissionPreparation preparation = claim(
			submission,
			now,
			deadline
		);
		if (retryingFailed) {
			metrics.recordRetry();
		} else {
			metrics.recordReclaimed();
		}
		return preparation;
	}

	private AnswerSubmissionPreparation claim(
		AnswerSubmission submission,
		Instant claimedAt,
		AnswerSubmissionDeadline deadline
	) {
		String processingToken = UUID.randomUUID().toString();
		submission.claim(
			processingToken,
			claimedAt,
			claimedAt.plus(policyProperties.processingLease())
		);
		return AnswerSubmissionPreparation.processing(
			submission.getId(),
			processingToken,
			AnswerAssessmentInput.from(submission),
			deadline
		);
	}

	private int getNextAttemptNo(Long sessionQuestionId) {
		int latestAnswerAttempt = answerRepository
			.findFirstBySessionQuestion_IdOrderByAttemptNoDesc(sessionQuestionId)
			.map(Answer::getAttemptNo)
			.orElse(0);
		int latestSubmissionAttempt = answerSubmissionRepository
			.findFirstBySessionQuestion_IdOrderByAttemptNoDesc(sessionQuestionId)
			.map(AnswerSubmission::getAttemptNo)
			.orElse(0);
		return Math.max(latestAnswerAttempt, latestSubmissionAttempt) + 1;
	}

	private void validateNoConflictingSubmission(Long sessionQuestionId) {
		if (answerSubmissionRepository
			.existsUnfinishedBySessionQuestionId(sessionQuestionId)) {
			throw new ApiException(ErrorCode.ANSWER_SUBMISSION_CONFLICT);
		}
	}

	private void validateSpeechAnswerNotUsed(Long speechAnswerId) {
		if (answerRepository.existsBySpeechAnswer_Id(speechAnswerId)) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"이미 답변 제출에 사용된 음성 답변입니다."
			);
		}
	}

	private void validateAttemptCount(int attemptNo) {
		if (!AnswerAttemptPolicy.isAllowed(attemptNo)) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"답변 가능 횟수를 초과했습니다."
			);
		}
	}

	private LearningSession getSessionForUpdate(Long sessionId) {
		return learningSessionRepository.findForUpdateById(sessionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			));
	}

	private SpeechAnswer getSpeechAnswer(Long speechAnswerId) {
		return speechAnswerRepository.findById(speechAnswerId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.SPEECH_ANSWER_NOT_FOUND
			));
	}

	private AnswerSubmission getSubmission(Long submissionId) {
		return answerSubmissionRepository.findById(submissionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.ANSWER_SUBMISSION_NOT_FOUND
			));
	}

	private AnswerSubmission getSubmissionForUpdate(Long submissionId) {
		return answerSubmissionRepository.findForUpdateById(submissionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.ANSWER_SUBMISSION_NOT_FOUND
			));
	}

	private void validateRequestPath(
		AnswerSubmission submission,
		Long sessionId,
		Long sessionQuestionId
	) {
		LearningSessionQuestion question = submission.getSessionQuestion();
		if (!Objects.equals(question.getLearningSession().getId(), sessionId)
			|| !Objects.equals(question.getId(), sessionQuestionId)) {
			throw new ApiException(ErrorCode.CURRENT_QUESTION_MISMATCH);
		}
	}

	private void validateInProgress(LearningSession session) {
		if (!session.isInProgress()) {
			throw new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS
			);
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

	private void validateSpeechAnswer(
		SpeechAnswer speechAnswer,
		LearningSessionQuestion currentQuestion
	) {
		if (!speechAnswer.isCompleted()) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"처리가 완료되지 않은 음성 답변입니다."
			);
		}
		if (!speechAnswer.isUsableFor(currentQuestion)) {
			throw new ApiException(ErrorCode.CURRENT_QUESTION_MISMATCH);
		}
	}

	private void validateProcessingToken(
		AnswerSubmission submission,
		String processingToken
	) {
		if (!submission.isProcessingWithToken(processingToken)) {
			throw new ApiException(
				ErrorCode.ANSWER_SUBMISSION_PROCESSING,
				"답변 제출 처리 권한이 만료되었습니다."
			);
		}
	}

	private void validateDeadline(AnswerSubmissionDeadline deadline) {
		if (deadline.isExpiredAt(clock.instant())) {
			throw new ApiException(ErrorCode.ANSWER_SUBMISSION_TIMEOUT);
		}
	}

	private String toFailureMessage(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			message = exception.getClass().getSimpleName();
		}
		return message.length() <= 1000
			? message
			: message.substring(0, 1000);
	}
}
