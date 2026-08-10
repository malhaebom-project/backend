package com.malhaebom.malhaebom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.malhaebom.malhaebom.domain.learning.AnswerEvaluation;
import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.port.AnswerAssessmentGenerator;

@ExtendWith(MockitoExtension.class)
class AnswerAssessmentServiceTest {

	private static final String ANSWER_TEXT = "He is running.";

	@Mock
	private AnswerAssessmentGenerator answerAssessmentGenerator;

	@Mock
	private AnswerEvaluator answerEvaluator;

	private AnswerAssessmentService answerAssessmentService;
	private Question question;

	@BeforeEach
	void setUp() {
		answerAssessmentService = new AnswerAssessmentService(
			answerAssessmentGenerator,
			answerEvaluator
		);
		question = createQuestion();
	}

	@Test
	void 검증된_AI_평가를_그대로_반환한다() {
		AnswerAssessment generated = new AnswerAssessment(
			true,
			48,
			27,
			18,
			List.of("is running"),
			List.of(),
			"현재진행형을 자연스럽게 잘 사용했어요!"
		);
		when(answerAssessmentGenerator.generate(question, ANSWER_TEXT))
			.thenReturn(generated);

		AnswerAssessment assessment = answerAssessmentService.assess(
			question,
			ANSWER_TEXT
		);

		assertSame(generated, assessment);
		verify(answerAssessmentGenerator).generate(question, ANSWER_TEXT);
		verifyNoInteractions(answerEvaluator);
	}

	@Test
	void AI_결과가_null이면_기본_평가를_반환한다() {
		when(answerAssessmentGenerator.generate(question, ANSWER_TEXT))
			.thenReturn(null);
		when(answerEvaluator.evaluate(question, ANSWER_TEXT))
			.thenReturn(AnswerEvaluation.from(AnswerResult.CORRECT));

		AnswerAssessment assessment = answerAssessmentService.assess(
			question,
			ANSWER_TEXT
		);

		assertEquals(AnswerResult.CORRECT, assessment.result());
		assertEquals(100, assessment.totalScore());
		assertEquals("정확하고 또박또박 잘 말했어요!", assessment.feedbackText());
	}

	@Test
	void AI_호출이나_출력_검증이_실패하면_기본_평가를_반환한다() {
		when(answerAssessmentGenerator.generate(question, ANSWER_TEXT))
			.thenThrow(new IllegalArgumentException("invalid AI output"));
		when(answerEvaluator.evaluate(question, ANSWER_TEXT))
			.thenReturn(AnswerEvaluation.from(AnswerResult.INCORRECT));

		AnswerAssessment assessment = answerAssessmentService.assess(
			question,
			ANSWER_TEXT
		);

		assertEquals(AnswerResult.INCORRECT, assessment.result());
		assertEquals(0, assessment.totalScore());
		assertEquals(
			"좋은 시도예요! 모범 답안을 참고해서 다시 말해 보세요.",
			assessment.feedbackText()
		);
	}

	private Question createQuestion() {
		return Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"소년은 무엇을 하고 있나요?",
			null,
			"The boy is running.",
			Set.of("He is running."),
			null,
			null
		);
	}
}
