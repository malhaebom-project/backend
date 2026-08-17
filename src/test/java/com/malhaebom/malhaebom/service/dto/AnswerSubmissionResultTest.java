package com.malhaebom.malhaebom.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

class AnswerSubmissionResultTest {

	@Test
	void 답변_엔티티의_응답_값을_스냅샷으로_복사한다() {
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
			AnswerEvaluation.from(AnswerResult.CORRECT),
			"현재진행형을 정확하게 사용했어요!"
		);
		ReflectionTestUtils.setField(answer, "id", 40L);

		AnswerSubmissionResult result = AnswerSubmissionResult.from(answer);

		assertEquals(40L, result.answerId());
		assertEquals(20L, result.sessionQuestionId());
		assertEquals(1, result.attemptNo());
		assertEquals("He is running.", result.answerText());
		assertEquals(AnswerResult.CORRECT, result.result());
		assertEquals(100, result.score());
		assertEquals("He is running.", result.modelAnswer());
		assertEquals(
			"현재진행형을 정확하게 사용했어요!",
			result.feedbackText()
		);
		assertFalse(result.canRetry());
		assertEquals(0, result.remainingAttempts());
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
