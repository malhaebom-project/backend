package com.malhaebom.malhaebom.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.global.exception.LearningSessionNotFoundException;
import com.malhaebom.malhaebom.service.model.AnswerSubmissionResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningAnswerService {

	private static final int MAX_ATTEMPT_COUNT = 2;

	private final LearningSessionRepository learningSessionRepository;
	private final AnswerRepository answerRepository;

	@Transactional
	public AnswerSubmissionResult submit(
		Long sessionId,
		Long sessionQuestionId,
		String answerText
	) {
		LearningSession session = learningSessionRepository
			.findWithQuestionsForUpdateById(sessionId)
			.orElseThrow(LearningSessionNotFoundException::new);
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, sessionQuestionId);

		int attemptNo = getNextAttemptNo(sessionQuestionId);
		if (attemptNo > MAX_ATTEMPT_COUNT) {
			throw new IllegalStateException("답변 가능 횟수를 초과했습니다.");
		}

		Answer answer = Answer.create(currentQuestion, answerText, attemptNo);
		answerRepository.save(answer);

		boolean canRetry = !answer.isCorrect()
			&& attemptNo < MAX_ATTEMPT_COUNT;
		if (!answer.isCorrect()) {
			session.recordWrongAnswerAttempt();
		}

		if (!canRetry) {
			session.completeCurrentQuestion(answer.isCorrect());
		}

		int remainingAttempts = canRetry
			? MAX_ATTEMPT_COUNT - attemptNo
			: 0;
		return new AnswerSubmissionResult(
			answer,
			canRetry,
			remainingAttempts
		);
	}

	@Transactional
	public LearningSession skipRetry(
		Long sessionId,
		Long sessionQuestionId
	) {
		LearningSession session = learningSessionRepository
			.findWithQuestionsForUpdateById(sessionId)
			.orElseThrow(LearningSessionNotFoundException::new);
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, sessionQuestionId);

		Answer latestAnswer = answerRepository
			.findFirstBySessionQuestion_IdOrderByAttemptNoDesc(sessionQuestionId)
			.orElseThrow(
				() -> new IllegalStateException("제출된 답변이 없습니다.")
			);
		validateRetryable(latestAnswer);

		session.completeCurrentQuestion(false);
		return session;
	}

	private int getNextAttemptNo(Long sessionQuestionId) {
		return answerRepository
			.findFirstBySessionQuestion_IdOrderByAttemptNoDesc(sessionQuestionId)
			.map(answer -> answer.getAttemptNo() + 1)
			.orElse(1);
	}

	private void validateCurrentQuestion(
		LearningSessionQuestion currentQuestion,
		Long sessionQuestionId
	) {
		if (!Objects.equals(currentQuestion.getId(), sessionQuestionId)) {
			throw new IllegalArgumentException("현재 진행 중인 문제가 아닙니다.");
		}
	}

	private void validateRetryable(Answer answer) {
		if (answer.isCorrect() || answer.getAttemptNo() >= MAX_ATTEMPT_COUNT) {
			throw new IllegalStateException("재시도 가능한 오답이 아닙니다.");
		}
	}
}
