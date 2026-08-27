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
import com.malhaebom.malhaebom.domain.learning.AnswerSubmission;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmissionProcessingException;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmissionReservationException;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmissionStatus;
import com.malhaebom.malhaebom.domain.learning.AnswerSubmissionTarget;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionAnswerSubmissionException;
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
	private final ChildProfileService childProfileService;
	private final AnswerSubmissionMetricsRecorder metrics;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public AnswerSubmissionPreparation prepare(
		Long userId,
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
		AnswerSubmissionDeadline deadline = AnswerSubmissionDeadline.startingAt(
			clock.instant(),
			policyProperties.processingTimeout()
		);
		LearningSession session = getSessionForUpdate(sessionId);
		childProfileService.getOwnedActive(userId, session.getChildId());
		answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswerId)
			.ifPresent(existing ->
				validateRequestPath(existing, sessionId, sessionQuestionId)
			);
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

		AnswerSubmissionTarget target = getAnswerSubmissionTarget(
			session,
			sessionQuestionId
		);
		SpeechAnswer speechAnswer = getSpeechAnswer(speechAnswerId);
		int attemptNo = getNextAttemptNo(sessionQuestionId);
		AnswerSubmission submission = reserveSubmission(
			target,
			speechAnswer,
			attemptNo
		);
		validateSpeechAnswerNotUsed(speechAnswerId);
		validateNoConflictingSubmission(sessionQuestionId);
		answerSubmissionRepository.save(submission);
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
		validateDeadline(deadline);
		Answer answer = answerRepository.save(completeSubmission(
			submission,
			processingToken,
			assessment
		));
		applyAnswerResult(session, answer);
		validateDeadline(deadline);
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
		if (submission == null) {
			return;
		}

		submission.failIfProcessingWithToken(
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

		validateSubmissionTarget(session, submission);
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

	private AnswerSubmission reserveSubmission(
		AnswerSubmissionTarget target,
		SpeechAnswer speechAnswer,
		int attemptNo
	) {
		try {
			return target.reserve(speechAnswer, attemptNo);
		} catch (AnswerSubmissionReservationException exception) {
			throw toApiException(exception);
		}
	}

	private AnswerSubmissionTarget getAnswerSubmissionTarget(
		LearningSession session,
		Long sessionQuestionId
	) {
		try {
			return session.answerSubmissionTarget(sessionQuestionId);
		} catch (LearningSessionAnswerSubmissionException exception) {
			throw toApiException(exception);
		}
	}

	private void validateSubmissionTarget(
		LearningSession session,
		AnswerSubmission submission
	) {
		try {
			session.validateAnswerSubmissionTarget(submission);
		} catch (LearningSessionAnswerSubmissionException exception) {
			throw toApiException(exception);
		}
	}

	private void applyAnswerResult(LearningSession session, Answer answer) {
		try {
			session.applyAnswerResult(answer);
		} catch (LearningSessionAnswerSubmissionException exception) {
			throw toApiException(exception);
		}
	}

	private ApiException toApiException(
		AnswerSubmissionReservationException exception
	) {
		return switch (exception.getReason()) {
			case SPEECH_ANSWER_QUESTION_MISMATCH -> new ApiException(
				ErrorCode.CURRENT_QUESTION_MISMATCH,
				exception
			);
			case SPEECH_ANSWER_NOT_COMPLETED -> new ApiException(
				ErrorCode.INVALID_REQUEST,
				"처리가 완료되지 않은 음성 답변입니다.",
				exception
			);
			case ATTEMPT_NOT_ALLOWED -> new ApiException(
				ErrorCode.INVALID_REQUEST,
				"답변 가능 횟수를 초과했습니다.",
				exception
			);
		};
	}

	private ApiException toApiException(
		LearningSessionAnswerSubmissionException exception
	) {
		return switch (exception.getReason()) {
			case SESSION_NOT_IN_PROGRESS -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS,
				exception
			);
			case CURRENT_QUESTION_MISMATCH -> new ApiException(
				ErrorCode.CURRENT_QUESTION_MISMATCH,
				exception
			);
		};
	}

	private Answer completeSubmission(
		AnswerSubmission submission,
		String processingToken,
		AnswerAssessment assessment
	) {
		try {
			return submission.complete(
				processingToken,
				assessment.toEvaluation(),
				assessment.feedbackText()
			);
		} catch (AnswerSubmissionProcessingException exception) {
			throw new ApiException(
				ErrorCode.ANSWER_SUBMISSION_PROCESSING,
				"답변 제출 처리 권한이 만료되었습니다.",
				exception
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
