package com.malhaebom.malhaebom.service;

import com.malhaebom.malhaebom.domain.learning.*;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerSubmissionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LearningAnswerRetryService {
	private final LearningSessionRepository learningSessionRepository;
	private final AnswerRepository answerRepository;
	private final AnswerSubmissionRepository answerSubmissionRepository;
	private final ChildProfileService childProfileService;

	@Transactional
	public void skipRetry(Long userId, Long sessionId, Long sessionQuestionId) {
		LearningSession session = learningSessionRepository
			.findForUpdateById(sessionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			));
		childProfileService.getOwnedActive(userId, session.getChildId());
		LearningSessionQuestion target = getRetrySkipTarget(
			session,
			sessionQuestionId
		);
		validateNoConflictingSubmission(target.getId());

		Answer latestAnswer = answerRepository
			.findFirstBySessionQuestion_IdOrderByAttemptNoDesc(target.getId())
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
		session.skipRetry(latestAnswer);
	}

	private LearningSessionQuestion getRetrySkipTarget(LearningSession session, Long sessionQuestionId) {
		try {
			return session.retrySkipTarget(sessionQuestionId);
		} catch (LearningSessionAnswerSubmissionException exception) {
			throw toApiException(exception);
		}
	}

	private void validateNoConflictingSubmission(Long sessionQuestionId) {
		if (answerSubmissionRepository
			.existsUnfinishedBySessionQuestionId(sessionQuestionId)) {
			throw new ApiException(ErrorCode.ANSWER_SUBMISSION_CONFLICT);
		}
	}

	private ApiException toApiException(LearningSessionAnswerSubmissionException exception) {
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
}
