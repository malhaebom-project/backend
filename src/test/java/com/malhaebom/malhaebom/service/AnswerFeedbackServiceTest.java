package com.malhaebom.malhaebom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.malhaebom.malhaebom.service.dto.AnswerFeedback;
import com.malhaebom.malhaebom.service.port.AnswerFeedbackGenerator;

@ExtendWith(MockitoExtension.class)
class AnswerFeedbackServiceTest {

	private static final String ANSWER_TEXT = "He is running.";

	@Mock
	private AnswerFeedbackGenerator answerFeedbackGenerator;

	private AnswerFeedbackService answerFeedbackService;

	@BeforeEach
	void setUp() {
		answerFeedbackService = new AnswerFeedbackService(
			answerFeedbackGenerator
		);
	}

	@Test
	void AI가_생성한_피드백을_정규화해서_반환한다() {
		Answer answer = createAnswer(AnswerResult.INCORRECT);
		Question question = answer.getSessionQuestion().getQuestion();
		when(answerFeedbackGenerator.generate(
			question,
			ANSWER_TEXT,
			AnswerResult.INCORRECT
		)).thenReturn(new AnswerFeedback(
			List.of(" running ", "running", "is", "extra"),
			List.of("boy", "present progressive"),
			"  동작 표현은 좋았어요. 주어를 확인해 보세요.  "
		));

		AnswerFeedback feedback = answerFeedbackService.generate(answer);

		assertEquals(
			List.of("running", "is", "extra"),
			feedback.matchedKeywords()
		);
		assertEquals(
			List.of("boy", "present progressive"),
			feedback.missingKeywords()
		);
		assertEquals(
			"동작 표현은 좋았어요. 주어를 확인해 보세요.",
			feedback.feedbackText()
		);
	}

	@Test
	void 정답에는_AI가_누락_키워드를_반환해도_제거한다() {
		Answer answer = createAnswer(AnswerResult.CORRECT);
		Question question = answer.getSessionQuestion().getQuestion();
		when(answerFeedbackGenerator.generate(
			question,
			ANSWER_TEXT,
			AnswerResult.CORRECT
		)).thenReturn(new AnswerFeedback(
			List.of("is running"),
			List.of("boy"),
			"현재진행형을 정확하게 사용했어요!"
		));

		AnswerFeedback feedback = answerFeedbackService.generate(answer);

		assertEquals(List.of(), feedback.missingKeywords());
		assertEquals(
			"현재진행형을 정확하게 사용했어요!",
			feedback.feedbackText()
		);
	}

	@Test
	void AI_요청이_실패하면_기본_피드백을_반환한다() {
		Answer answer = createAnswer(AnswerResult.INCORRECT);
		Question question = answer.getSessionQuestion().getQuestion();
		when(answerFeedbackGenerator.generate(
			question,
			ANSWER_TEXT,
			AnswerResult.INCORRECT
		)).thenThrow(new IllegalStateException("OpenAI unavailable"));

		AnswerFeedback feedback = answerFeedbackService.generate(answer);

		assertEquals(List.of(), feedback.matchedKeywords());
		assertEquals(List.of(), feedback.missingKeywords());
		assertEquals(
			"좋은 시도예요! 모범 답안을 참고해서 다시 말해 보세요.",
			feedback.feedbackText()
		);
	}

	private Answer createAnswer(AnswerResult result) {
		Question question = Question.create(
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
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(question)
		);
		LearningSessionQuestion sessionQuestion = session.getCurrentQuestion();
		SpeechAnswer speechAnswer = SpeechAnswer.start(
			sessionQuestion,
			"request-key",
			1
		);
		speechAnswer.complete(ANSWER_TEXT, 0.94, "TEST_STT");
		return Answer.create(
			sessionQuestion,
			speechAnswer,
			1,
			AnswerEvaluation.from(result)
		);
	}
}
