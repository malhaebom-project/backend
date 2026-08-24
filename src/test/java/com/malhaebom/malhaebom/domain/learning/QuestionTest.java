package com.malhaebom.malhaebom.domain.learning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class QuestionTest {

	@Test
	void 모범_답안의_대소문자와_공백과_문장부호를_정규화해_비교한다() {
		Question question = createQuestion();

		assertTrue(question.matchesAnswer("  THE   BOY IS RUNNING!  "));
	}

	@Test
	void 등록된_허용_답안도_정답으로_판정한다() {
		Question question = createQuestion();

		AnswerEvaluation evaluation = question.evaluateAnswer("He's running.");

		assertEquals(AnswerResult.CORRECT, evaluation.result());
		assertEquals(100, evaluation.score());
	}

	@Test
	void 등록되지_않은_답안은_오답으로_판정한다() {
		Question question = createQuestion();
		AnswerEvaluation evaluation = question.evaluateAnswer(
			"He is walking."
		);

		assertEquals(AnswerResult.INCORRECT, evaluation.result());
		assertEquals(0, evaluation.score());
		assertFalse(question.matchesAnswer(" "));
	}

	@Test
	void 비어_있는_모범_답안으로_문제를_생성할_수_없다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> createQuestion(" ", Set.of("He is running."))
		);
	}

	@Test
	void 비어_있는_허용_답안으로_문제를_생성할_수_없다() {
		assertThrows(
			IllegalArgumentException.class,
			() -> createQuestion("The boy is running.", Set.of(" "))
		);
	}

	private Question createQuestion() {
		return createQuestion(
			"The boy is running.",
			Set.of("He is running.", "He's running.")
		);
	}

	private Question createQuestion(
		String modelAnswer,
		Set<String> acceptedAnswers
	) {
		return Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			null,
			"",
			modelAnswer,
			acceptedAnswers,
			"He is ____ing.",
			null
		);
	}
}
