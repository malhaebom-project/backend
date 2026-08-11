package com.malhaebom.malhaebom.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningHintService {

	private final LearningSessionRepository learningSessionRepository;

	@Transactional
	public Question request(Long sessionId, Long questionId) {
		LearningSession session = learningSessionRepository
			.findForUpdateById(sessionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			));
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, questionId);
		validateHint(currentQuestion.getQuestion());

		session.useHintOnCurrentQuestion();
		return currentQuestion.getQuestion();
	}

	private void validateCurrentQuestion(
		LearningSessionQuestion currentQuestion,
		Long questionId
	) {
		if (!Objects.equals(currentQuestion.getQuestion().getId(), questionId)) {
			throw new ApiException(ErrorCode.CURRENT_QUESTION_MISMATCH);
		}
	}

	private void validateHint(Question question) {
		if (question.getHintText() == null || question.getHintText().isBlank()) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"현재 문제에 등록된 힌트가 없습니다."
			);
		}
	}
}
