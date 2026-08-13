package com.malhaebom.malhaebom.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.Answer;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnswerSubmissionTransactionService {

	private static final int MAX_ATTEMPT_COUNT = 2;
	private static final Duration PROCESSING_LEASE = Duration.ofSeconds(60);
	private static final Set<AnswerSubmissionStatus> BLOCKING_STATUSES = Set.of(
		AnswerSubmissionStatus.PENDING,
		AnswerSubmissionStatus.PROCESSING,
		AnswerSubmissionStatus.FAILED
	);

	private final LearningSessionRepository learningSessionRepository;
	private final SpeechAnswerRepository speechAnswerRepository;
	private final AnswerRepository answerRepository;
	private final AnswerSubmissionRepository answerSubmissionRepository;

	@Transactional
	public AnswerSubmissionPreparation prepare(
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
		AnswerSubmission existing = answerSubmissionRepository
			.findBySpeechAnswer_Id(speechAnswerId)
			.orElse(null);
		if (existing != null) {
			validateRequestPath(existing, sessionId, sessionQuestionId);
		}

		LearningSession session = getSessionForUpdate(sessionId);
		AnswerSubmission lockedExisting = answerSubmissionRepository
			.findForUpdateBySpeechAnswer_Id(speechAnswerId)
			.orElse(null);
		if (lockedExisting != null) {
			return resolveExisting(
				lockedExisting,
				session,
				sessionQuestionId,
				Instant.now()
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
		return claim(submission, Instant.now());
	}

	@Transactional
	public AnswerSubmissionResult complete(
		Long submissionId,
		String processingToken,
		AnswerAssessment assessment
	) {
		AnswerSubmission found = getSubmission(submissionId);
		LearningSession session = getSessionForUpdate(
			found.getSessionQuestion().getLearningSession().getId()
		);
		AnswerSubmission submission = getSubmissionForUpdate(submissionId);
		validateProcessingToken(submission, processingToken);
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
		boolean canRetry = !answer.isCorrect()
			&& submission.getAttemptNo() < MAX_ATTEMPT_COUNT;
		if (canRetry) {
			session.recordWrongAnswerAttempt();
		} else {
			session.completeCurrentQuestion(answer.isCorrect());
		}
		submission.complete(processingToken, answer);

		return toResult(answer);
	}

	@Transactional
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
		Instant now
	) {
		validateRequestPath(submission, session.getId(), sessionQuestionId);
		if (submission.isCompleted()) {
			return AnswerSubmissionPreparation.completed(
				submission.getId(),
				toResult(submission.getAnswer())
			);
		}

		if (submission.isProcessing()
			&& !submission.isLeaseExpiredAt(now)) {
			throw new ApiException(ErrorCode.ANSWER_SUBMISSION_PROCESSING);
		}

		validateInProgress(session);
		validateCurrentQuestion(
			session.getCurrentQuestion(),
			submission.getSessionQuestion().getId()
		);
		if (submission.getStatus() == AnswerSubmissionStatus.FAILED) {
			submission.retry();
		}
		return claim(submission, now);
	}

	private AnswerSubmissionPreparation claim(
		AnswerSubmission submission,
		Instant claimedAt
	) {
		String processingToken = UUID.randomUUID().toString();
		submission.claim(
			processingToken,
			claimedAt,
			claimedAt.plus(PROCESSING_LEASE)
		);
		return AnswerSubmissionPreparation.processing(
			submission.getId(),
			processingToken,
			new AnswerAssessmentInput(
				submission.getQuestionTextSnapshot(),
				submission.getQuestionTextKoSnapshot(),
				submission.getModelAnswerSnapshot(),
				submission.getAcceptedAnswersSnapshot(),
				submission.getAnswerTextSnapshot()
			)
		);
	}

	private AnswerSubmissionResult toResult(Answer answer) {
		if (answer == null) {
			throw new IllegalStateException("완료된 답변 제출에 답변 결과가 없습니다.");
		}
		boolean canRetry = !answer.isCorrect()
			&& answer.getAttemptNo() < MAX_ATTEMPT_COUNT;
		int remainingAttempts = canRetry
			? MAX_ATTEMPT_COUNT - answer.getAttemptNo()
			: 0;
		return new AnswerSubmissionResult(
			answer,
			canRetry,
			remainingAttempts
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
			.existsBySessionQuestion_IdAndStatusIn(
				sessionQuestionId,
				BLOCKING_STATUSES
			)) {
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
		if (attemptNo > MAX_ATTEMPT_COUNT) {
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
