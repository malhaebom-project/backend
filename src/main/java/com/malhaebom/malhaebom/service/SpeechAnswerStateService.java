package com.malhaebom.malhaebom.service;

import java.util.Objects;

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpeechAnswerStateService {

	private final LearningSessionRepository learningSessionRepository;
	private final SpeechAnswerRepository speechAnswerRepository;

	@Transactional
	public SpeechAnswer start(
		Long sessionId,
		Long sessionQuestionId,
		String requestKey
	) {
		validateRequestKey(requestKey);

		LearningSession session = learningSessionRepository
			.findForUpdateById(sessionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			));
		validateInProgress(session);
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, sessionQuestionId);

		return speechAnswerRepository.findByRequestKey(requestKey)
			.map(existing -> resolveExisting(existing, currentQuestion))
			.orElseGet(
				() -> create(currentQuestion, requestKey)
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
	public SpeechAnswer complete(
		Long speechAnswerId,
		String transcript,
		Double confidence,
		String sttProvider
	) {
		SpeechAnswer speechAnswer = getSpeechAnswer(speechAnswerId);
		speechAnswer.complete(transcript, confidence, sttProvider);
		return speechAnswer;
	}

	@Transactional
	public SpeechAnswer fail(
		Long speechAnswerId,
		String failureMessage,
		String sttProvider
	) {
		SpeechAnswer speechAnswer = getSpeechAnswer(speechAnswerId);
		speechAnswer.fail(failureMessage, sttProvider);
		return speechAnswer;
	}

	private SpeechAnswer create(
		LearningSessionQuestion currentQuestion,
		String requestKey
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
			recordingNo
		);
		return speechAnswerRepository.saveAndFlush(speechAnswer);
	}

	private SpeechAnswer resolveExisting(
		SpeechAnswer existing,
		LearningSessionQuestion currentQuestion
	) {
		if (!isSameQuestion(existing.getSessionQuestion(), currentQuestion)) {
			throw new ApiException(ErrorCode.CURRENT_QUESTION_MISMATCH);
		}

		if (existing.isCompleted()) {
			return existing;
		}

		if (
			existing.getProcessingStatus()
				== SpeechProcessingStatus.PROCESSING
		) {
			throw new ApiException(ErrorCode.SPEECH_PROCESSING);
		}

		throw new ApiException(ErrorCode.STT_PROCESSING_FAILED);
	}

	private SpeechAnswer getSpeechAnswer(Long speechAnswerId) {
		return speechAnswerRepository.findById(speechAnswerId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.SPEECH_ANSWER_NOT_FOUND
			));
	}

	private boolean isSameQuestion(
		LearningSessionQuestion first,
		LearningSessionQuestion second
	) {
		if (first == second) {
			return true;
		}

		return first != null
			&& second != null
			&& first.getId() != null
			&& Objects.equals(first.getId(), second.getId());
	}

	private void validateCurrentQuestion(
		LearningSessionQuestion currentQuestion,
		Long sessionQuestionId
	) {
		if (!Objects.equals(currentQuestion.getId(), sessionQuestionId)) {
			throw new ApiException(ErrorCode.CURRENT_QUESTION_MISMATCH);
		}
	}

	private void validateRequestKey(String requestKey) {
		if (requestKey == null || requestKey.isBlank()) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"중복 요청 방지를 위한 요청 식별 키가 필요합니다."
			);
		}

		if (requestKey.length() > 100) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"요청 식별 키는 100자를 초과할 수 없습니다."
			);
		}
	}
}
