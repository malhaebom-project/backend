package com.malhaebom.malhaebom.service.speech;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.SpeechProcessingStatus;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerStartResult;
import com.malhaebom.malhaebom.service.policy.SpeechProcessingLease;
import com.malhaebom.malhaebom.service.ChildProfileService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpeechAnswerStateService {
	private final LearningSessionRepository learningSessionRepository;
	private final SpeechAnswerRepository speechAnswerRepository;
	private final SpeechProcessingLease processingLease;
	private final ChildProfileService childProfileService;

	@Transactional
	public SpeechAnswerStartResult start(
		Long userId,
		Long sessionId,
		Long sessionQuestionId,
		String requestKey
	) {
		LearningSession session = learningSessionRepository
			.findForUpdateById(sessionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			));
		childProfileService.getOwnedActive(userId, session.getChildId());
		validateInProgress(session);
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, sessionQuestionId);

		List<String> adaptationPhrases = currentQuestion.getQuestion()
			.getAcceptedAnswers()
			.stream()
			.toList();
		Instant now = Instant.now();
		return speechAnswerRepository
			.findForUpdateByRequestKey(requestKey)
			.map(existing -> resolveExisting(
				existing,
				currentQuestion,
				adaptationPhrases,
				now
			))
			.orElseGet(
				() -> SpeechAnswerStartResult.claimed(
					create(currentQuestion, requestKey, now),
					adaptationPhrases
				)
			);
	}

	private void validateInProgress(LearningSession session) {
		if (!session.isInProgress()) {
			throw new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS
			);
		}
	}

	@Transactional
	public Optional<SpeechAnswer> complete(
		Long speechAnswerId,
		String processingToken,
		String transcript,
		Double confidence,
		String sttProvider
	) {
		SpeechAnswer speechAnswer = getSpeechAnswerForUpdate(speechAnswerId);
		if (!speechAnswer.isProcessingWithToken(processingToken)) {
			return Optional.empty();
		}
		speechAnswer.complete(
			processingToken,
			transcript,
			confidence,
			sttProvider
		);
		return Optional.of(speechAnswer);
	}

	@Transactional
	public boolean fail(
		Long speechAnswerId,
		String processingToken,
		String failureMessage,
		String sttProvider
	) {
		SpeechAnswer speechAnswer = getSpeechAnswerForUpdate(speechAnswerId);
		if (!speechAnswer.isProcessingWithToken(processingToken)) {
			return false;
		}
		speechAnswer.fail(processingToken, failureMessage, sttProvider);
		return true;
	}

	private SpeechAnswer create(
		LearningSessionQuestion currentQuestion,
		String requestKey,
		Instant claimedAt
	) {
		int recordingNo = speechAnswerRepository
			.findFirstBySessionQuestion_IdOrderByRecordingNoDesc(
				currentQuestion.getId()
			)
			.map(answer -> answer.getRecordingNo() + 1)
			.orElse(1);
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			currentQuestion,
			requestKey,
			recordingNo,
			newProcessingToken(),
			claimedAt,
			leaseExpiresAt(claimedAt)
		);
		return speechAnswerRepository.saveAndFlush(speechAnswer);
	}

	private SpeechAnswerStartResult resolveExisting(
		SpeechAnswer existing,
		LearningSessionQuestion currentQuestion,
		List<String> adaptationPhrases,
		Instant now
	) {
		if (!isSameQuestion(existing.getSessionQuestion(), currentQuestion)) {
			throw new ApiException(ErrorCode.CURRENT_QUESTION_MISMATCH);
		}

		if (existing.isCompleted()) {
			return SpeechAnswerStartResult.completed(
				existing,
				adaptationPhrases
			);
		}

		if (existing.getProcessingStatus() == SpeechProcessingStatus.PROCESSING) {
			if (!existing.isLeaseExpiredAt(now)) {
				return SpeechAnswerStartResult.processing(
					existing,
					adaptationPhrases
				);
			}

			existing.reclaim(
				newProcessingToken(),
				now,
				leaseExpiresAt(now)
			);
			return SpeechAnswerStartResult.claimed(
				existing,
				adaptationPhrases
			);
		}

		throw new ApiException(ErrorCode.STT_PROCESSING_FAILED);
	}

	private SpeechAnswer getSpeechAnswerForUpdate(Long speechAnswerId) {
		return speechAnswerRepository.findForUpdateById(speechAnswerId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.SPEECH_ANSWER_NOT_FOUND
			));
	}

	private String newProcessingToken() {
		return UUID.randomUUID().toString();
	}

	private Instant leaseExpiresAt(Instant claimedAt) {
		return claimedAt.plus(processingLease.value());
	}

	private boolean isSameQuestion(LearningSessionQuestion first, LearningSessionQuestion second) {
		if (first == second) {
			return true;
		}

		return first != null
			&& second != null
			&& first.getId() != null
			&& Objects.equals(first.getId(), second.getId());
	}

	private void validateCurrentQuestion(LearningSessionQuestion currentQuestion, Long sessionQuestionId) {
		if (!Objects.equals(currentQuestion.getId(), sessionQuestionId)) {
			throw new ApiException(ErrorCode.CURRENT_QUESTION_MISMATCH);
		}
	}
}
