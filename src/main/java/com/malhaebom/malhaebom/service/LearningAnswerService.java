package com.malhaebom.malhaebom.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.domain.learning.repository.AnswerRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.SpeechAnswerRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearningAnswerService {

	private static final int MAX_ATTEMPT_COUNT = 2;

	private final LearningSessionRepository learningSessionRepository;
	private final AnswerRepository answerRepository;
	private final SpeechAnswerRepository speechAnswerRepository;
	private final AnswerAssessmentService answerAssessmentService;

	@Transactional
	public AnswerSubmissionResult submit(
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
		LearningSession session = learningSessionRepository
			.findForUpdateById(sessionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			));
		validateInProgress(session);
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, sessionQuestionId);
		SpeechAnswer speechAnswer = getSpeechAnswer(speechAnswerId);
		validateSpeechAnswer(speechAnswer, currentQuestion);
		validateSpeechAnswerNotUsed(speechAnswerId);

		int attemptNo = getNextAttemptNo(sessionQuestionId);
		if (attemptNo > MAX_ATTEMPT_COUNT) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"답변 가능 횟수를 초과했습니다."
			);
		}

		AnswerAssessment assessment = answerAssessmentService.assess(
			currentQuestion.getQuestion(),
			speechAnswer.getTranscript()
		);
		Answer answer = Answer.create(
			currentQuestion,
			speechAnswer,
			attemptNo,
			assessment.toEvaluation(),
			assessment.feedbackText()
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

	@Transactional
	public void skipRetry(Long sessionId, Long sessionQuestionId) {
		LearningSession session = learningSessionRepository
			.findForUpdateById(sessionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			));
		validateInProgress(session);
		LearningSessionQuestion currentQuestion = session.getCurrentQuestion();
		validateCurrentQuestion(currentQuestion, sessionQuestionId);

		Answer latestAnswer = answerRepository
			.findFirstBySessionQuestion_IdOrderByAttemptNoDesc(sessionQuestionId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.INVALID_REQUEST,
				"오답 제출 후에만 재시도를 건너뛸 수 있습니다."
			));
		if (latestAnswer.isCorrect()
			|| latestAnswer.getAttemptNo() >= MAX_ATTEMPT_COUNT) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"재시도 가능한 오답이 아닙니다."
			);
		}

		session.skipRetryOnCurrentQuestion();
	}

	private void validateInProgress(LearningSession session) {
		if (!session.isInProgress()) {
			throw new ApiException(
				ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS
			);
		}
	}

	private SpeechAnswer getSpeechAnswer(Long speechAnswerId) {
		return speechAnswerRepository.findById(speechAnswerId)
			.orElseThrow(() -> new ApiException(
				ErrorCode.SPEECH_ANSWER_NOT_FOUND
			));
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

	private void validateSpeechAnswerNotUsed(Long speechAnswerId) {
		if (answerRepository.existsBySpeechAnswer_Id(speechAnswerId)) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
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
			throw new ApiException(ErrorCode.CURRENT_QUESTION_MISMATCH);
		}
	}
}
