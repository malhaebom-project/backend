package com.malhaebom.malhaebom.presentation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.malhaebom.malhaebom.domain.learning.Answer;
import com.malhaebom.malhaebom.domain.learning.AnswerEvaluation;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionQuestion;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerSubmissionResult;

class SubmitAnswerResponseTest {

	@Test
	void 제출_결과를_AI_피드백_응답으로_변환한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			"request-key",
			1
		);
		speechAnswer.complete("He is running.", 0.94, "TEST_STT");
		Answer answer = Answer.create(
			sessionQuestion,
			speechAnswer,
			1,
			AnswerEvaluation.from(AnswerResult.CORRECT)
		);
		ReflectionTestUtils.setField(answer, "id", 40L);
		AnswerSubmissionResult submission = new AnswerSubmissionResult(
			answer,
			new AnswerAssessment(
				true,
				50,
				30,
				20,
				"현재진행형을 정확하게 사용했어요!"
			),
			false,
			0
		);

		SubmitAnswerResponse response = SubmitAnswerResponse.from(submission);

		assertEquals(40L, response.answerId());
		assertEquals(20L, response.sessionQuestionId());
		assertEquals(1, response.attemptNo());
		assertEquals("He is running.", response.answerText());
		assertEquals(AnswerResult.CORRECT, response.result());
		assertEquals(100, response.score());
		assertEquals(
			"현재진행형을 정확하게 사용했어요!",
			response.feedbackText()
		);
		assertNull(response.feedbackTtsUrl());
		assertFalse(response.canRetry());
		assertEquals(0, response.remainingAttempts());
	}

	private LearningSessionQuestion createSessionQuestion() {
		Question question = Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			null,
			"He is running.",
			Set.of("He is running."),
			null,
			null
		);
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);
		LearningSessionQuestion sessionQuestion = session.getCurrentQuestion();
		ReflectionTestUtils.setField(sessionQuestion, "id", 20L);
		return sessionQuestion;
	}
}
