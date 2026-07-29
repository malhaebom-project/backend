package com.malhaebom.malhaebom.domain.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class AnswerTest {

	@Test
	void 모범_답안을_제출하면_정답_시도를_생성한다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();

		Answer answer = Answer.create(
			sessionQuestion,
			"The boy is running.",
			1
		);

		assertSame(sessionQuestion, answer.getSessionQuestion());
		assertEquals(1, answer.getAttemptNo());
		assertEquals("The boy is running.", answer.getAnswerText());
		assertEquals(AnswerResult.CORRECT, answer.getResult());
		assertEquals(100, answer.getScore());
		assertEquals("The boy is running.", answer.getModelAnswerSnapshot());
		assertNotNull(answer.getSubmittedAt());
		assertTrue(answer.isCorrect());
	}

	@Test
	void 허용_답안을_제출해도_정답으로_판정한다() {
		Answer answer = Answer.create(
			createSessionQuestion(),
			"He's running.",
			1
		);

		assertEquals(AnswerResult.CORRECT, answer.getResult());
		assertEquals(100, answer.getScore());
	}

	@Test
	void 등록되지_않은_답안을_제출하면_오답_시도를_생성한다() {
		Answer answer = Answer.create(
			createSessionQuestion(),
			"He is walking.",
			2
		);

		assertEquals(2, answer.getAttemptNo());
		assertEquals(AnswerResult.INCORRECT, answer.getResult());
		assertEquals(0, answer.getScore());
		assertFalse(answer.isCorrect());
	}

	@Test
	void 세션_문제_없이_답변을_생성할_수_없다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(null, "The boy is running.", 1)
		);
	}

	@Test
	void 완료한_문제에는_답변을_생성할_수_없다() {
		LearningSessionQuestion sessionQuestion = createSessionQuestion();
		sessionQuestion.getLearningSession().completeCurrentQuestion(true);

		assertThrows(
			IllegalStateException.class,
			() -> Answer.create(sessionQuestion, "The boy is running.", 2)
		);
	}

	@Test
	void 비어_있는_답변으로_답변을_생성할_수_없다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(createSessionQuestion(), " ", 1)
		);
	}

	@Test
	void 답변_시도_번호는_1_이상이어야_한다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> Answer.create(
				createSessionQuestion(),
				"The boy is running.",
				0
			)
		);
	}

	private LearningSessionQuestion createSessionQuestion() {
		Question question = Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			null,
			"The boy is running.",
			Set.of("He is running.", "He's running."),
			"He is ____ing.",
			null
		);
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);

		return session.getCurrentQuestion();
	}
}
