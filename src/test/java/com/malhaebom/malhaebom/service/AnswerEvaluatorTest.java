package com.malhaebom.malhaebom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.malhaebom.malhaebom.domain.learning.AnswerEvaluation;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;

class AnswerEvaluatorTest {

	private final AnswerEvaluator answerEvaluator = new AnswerEvaluator();

	@Test
	void 모범_답안과_허용_답안을_정답으로_평가한다() {
		Question question = createQuestion();

		AnswerEvaluation modelAnswer = answerEvaluator.evaluate(
			question,
			"The boy is running."
		);
		AnswerEvaluation acceptedAnswer = answerEvaluator.evaluate(
			question,
			"He's running."
		);

		assertEquals(AnswerResult.CORRECT, modelAnswer.result());
		assertEquals(100, modelAnswer.score());
		assertEquals(AnswerResult.CORRECT, acceptedAnswer.result());
		assertEquals(100, acceptedAnswer.score());
	}

	@Test
	void 등록되지_않은_답안을_오답으로_평가한다() {
		AnswerEvaluation evaluation = answerEvaluator.evaluate(
			createQuestion(),
			"He is walking."
		);

		assertEquals(AnswerResult.INCORRECT, evaluation.result());
		assertEquals(0, evaluation.score());
	}

	private Question createQuestion() {
		return Question.create(
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
	}
}
