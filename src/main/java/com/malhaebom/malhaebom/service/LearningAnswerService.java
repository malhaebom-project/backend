package com.malhaebom.malhaebom.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerEvaluation;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.CurrentQuestionMismatchException;
import com.malhaebom.malhaebom.global.exception.LearningSessionNotFoundException;
import com.malhaebom.malhaebom.global.exception.SpeechAnswerNotFoundException;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningAnswerService {

	private static final int MAX_ATTEMPT_COUNT = 2;

	private final LearningSessionRepository learningSessionRepository;
	private final AnswerRepository answerRepository;
	private final SpeechAnswerRepository speechAnswerRepository;
	private final AnswerEvaluator answerEvaluator;

	@Transactional
	public AnswerSubmissionResult submit(
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
		LearningSession session = learningSessionRepository
			.findForUpdateById(sessionId)
			.orElseThrow(LearningSessionNotFoundException::new);
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, sessionQuestionId);
		SpeechAnswer speechAnswer = getSpeechAnswer(speechAnswerId);
		validateSpeechAnswer(speechAnswer, currentQuestion);
		validateSpeechAnswerNotUsed(speechAnswerId);

		int attemptNo = getNextAttemptNo(sessionQuestionId);
		if (attemptNo > MAX_ATTEMPT_COUNT) {
			throw new IllegalStateException("답변 가능 횟수를 초과했습니다.");
		}

		AnswerEvaluation evaluation = answerEvaluator.evaluate(
			currentQuestion.getQuestion(),
			speechAnswer.getTranscript()
		);
		Answer answer = Answer.create(
			currentQuestion,
			speechAnswer,
			attemptNo,
			evaluation
		);
		answerRepository.save(answer);

		boolean canRetry = !answer.isCorrect()
			&& attemptNo < MAX_ATTEMPT_COUNT;
		if (canRetry) {
			session.recordWrongAnswerAttempt();
		} else {
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

	private SpeechAnswer getSpeechAnswer(Long speechAnswerId) {
		return speechAnswerRepository.findById(speechAnswerId)
			.orElseThrow(SpeechAnswerNotFoundException::new);
	}

	private void validateSpeechAnswer(
		SpeechAnswer speechAnswer,
		LearningSessionQuestion currentQuestion
	) {
		if (!speechAnswer.isCompleted()) {
			throw new IllegalStateException(
				"처리가 완료되지 않은 음성 답변입니다."
			);
		}

		if (!speechAnswer.isUsableFor(currentQuestion)) {
			throw new CurrentQuestionMismatchException();
		}
	}

	private void validateSpeechAnswerNotUsed(Long speechAnswerId) {
		if (answerRepository.existsBySpeechAnswer_Id(speechAnswerId)) {
			throw new IllegalStateException(
				"이미 답변 제출에 사용된 음성 답변입니다."
			);
		}
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
			throw new CurrentQuestionMismatchException();
		}
	}
}
