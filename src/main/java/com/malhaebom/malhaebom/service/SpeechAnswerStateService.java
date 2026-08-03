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
import com.malhaebom.malhaebom.global.exception.CurrentQuestionMismatchException;
import com.malhaebom.malhaebom.global.exception.LearningSessionNotFoundException;
import com.malhaebom.malhaebom.global.exception.SpeechAnswerNotFoundException;
import com.malhaebom.malhaebom.global.exception.SpeechProcessingException;
import com.malhaebom.malhaebom.global.exception.SpeechProcessingFailedException;

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
			.orElseThrow(LearningSessionNotFoundException::new);
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, sessionQuestionId);

		return speechAnswerRepository.findByRequestKey(requestKey)
			.map(existing -> resolveExisting(existing, currentQuestion))
			.orElseGet(
				() -> create(currentQuestion, requestKey)
			);
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
			throw new CurrentQuestionMismatchException();
		}

		if (existing.isCompleted()) {
			return existing;
		}

		if (
			existing.getProcessingStatus()
				== SpeechProcessingStatus.PROCESSING
		) {
			throw new SpeechProcessingException();
		}

		throw new SpeechProcessingFailedException();
	}

	private SpeechAnswer getSpeechAnswer(Long speechAnswerId) {
		return speechAnswerRepository.findById(speechAnswerId)
			.orElseThrow(SpeechAnswerNotFoundException::new);
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
			throw new CurrentQuestionMismatchException();
		}
	}

	private void validateRequestKey(String requestKey) {
		if (requestKey == null || requestKey.isBlank()) {
			throw new IllegalArgumentException("멱등키는 비어 있을 수 없습니다.");
		}

		if (requestKey.length() > 100) {
			throw new IllegalArgumentException(
				"멱등키는 100자를 초과할 수 없습니다."
			);
		}
	}
}
