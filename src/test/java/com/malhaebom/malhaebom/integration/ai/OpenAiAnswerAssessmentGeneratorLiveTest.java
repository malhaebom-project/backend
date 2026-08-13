package com.malhaebom.malhaebom.integration.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.infra.ai.OpenAiAnswerAssessmentGenerator;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

/**
 * 실제 OpenAI 답변 평가 테스트입니다.
 *
 * <p>{@code config/application.yaml}에 {@code OPENAI_API_KEY}를 설정하고
 * {@code .\gradlew.bat liveTest}로 실행합니다. 기본 {@code test}에서는
 * 실행되지 않습니다.
 */
@Tag("live")
@SpringBootTest(
	properties = "spring.ai.openai.api-key=${OPENAI_API_KEY}"
)
@ActiveProfiles("test")
@Import(OpenAiAnswerAssessmentGeneratorLiveTest.SpeechTestConfiguration.class)
class OpenAiAnswerAssessmentGeneratorLiveTest {

	@Autowired
	private OpenAiAnswerAssessmentGenerator generator;

	@Test
	void STT_표기_차이는_감점하거나_피드백하지_않는다() {
		Question elephantQuestion = question(
			LearningTopic.ANIMAL,
			QuestionType.SHORT_ANSWER,
			"Is the elephant big?",
			"코끼리는 큰가요?",
			"Yes, it is.",
			Set.of("Yes.")
		);

		for (String answerText : List.of("yes", "Yes.", "YES!")) {
			AnswerAssessment assessment = generator.generate(
				assessmentInput(elephantQuestion, answerText)
			);

			assertEquals(
				AnswerResult.CORRECT,
				assessment.result(),
				() -> answerText + ": " + assessment
			);
			assertTrue(
				assessment.grammarScore() >= 18,
				() -> answerText + ": " + assessment
			);
			assertTrue(
				!containsSttFormattingAdvice(assessment.feedbackText()),
				() -> answerText + ": " + assessment
			);
		}
	}

	@Test
	void 대표_문제와_답안을_실제_AI로_평가한다() {
		for (LiveCase liveCase : liveCases()) {
			AnswerAssessment assessment = generator.generate(
				assessmentInput(
					liveCase.question(),
					liveCase.answerText()
				)
			);

			assertEquals(
				liveCase.expectedResult(),
				assessment.result(),
				() -> liveCase.name() + ": " + assessment
			);
			if (liveCase.maximumMeaningScore() != null) {
				assertTrue(
					assessment.meaningScore()
						<= liveCase.maximumMeaningScore(),
					() -> liveCase.name() + ": " + assessment
				);
			}
		}
	}

	private List<LiveCase> liveCases() {
		Question runningQuestion = question(
			LearningTopic.DAILY_LIFE,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			"The boy is running.",
			Set.of("He is running.", "He's running.")
		);
		Question bookQuestion = question(
			LearningTopic.DAILY_LIFE,
			QuestionType.SHORT_ANSWER,
			"What is this?",
			"이것은 무엇인가요?",
			"It is a book.",
			Set.of("It's a book.")
		);
		Question lionQuestion = question(
			LearningTopic.ANIMAL,
			QuestionType.PICTURE_DESCRIPTION,
			"What animal is this?",
			"이 동물은 무엇인가요?",
			"It is a lion.",
			Set.of("It's a lion.")
		);

		return List.of(
			new LiveCase(
				"현재진행형 허용 답안",
				runningQuestion,
				"He is running.",
				AnswerResult.CORRECT,
				null
			),
			new LiveCase(
				"현재진행형 축약 답안",
				runningQuestion,
				"He's running.",
				AnswerResult.CORRECT,
				null
			),
			new LiveCase(
				"현재진행형 문법 오류",
				runningQuestion,
				"He is run.",
				AnswerResult.PARTIALLY_CORRECT,
				null
			),
			new LiveCase(
				"현재진행형 의미 오답",
				runningQuestion,
				"He is walking.",
				AnswerResult.INCORRECT,
				29
			),
			new LiveCase(
				"사물 식별 허용 답안",
				bookQuestion,
				"It's a book.",
				AnswerResult.CORRECT,
				null
			),
			new LiveCase(
				"사물 식별 자연스러운 변형",
				bookQuestion,
				"This is a book.",
				AnswerResult.CORRECT,
				null
			),
			new LiveCase(
				"사물 식별 문법 오류",
				bookQuestion,
				"It is books.",
				AnswerResult.PARTIALLY_CORRECT,
				null
			),
			new LiveCase(
				"사물 식별 의미 오답",
				bookQuestion,
				"It is a pen.",
				AnswerResult.INCORRECT,
				29
			),
			new LiveCase(
				"동물 식별 모범 답안",
				lionQuestion,
				"It is a lion.",
				AnswerResult.CORRECT,
				null
			),
			new LiveCase(
				"동물 식별 축약 답안",
				lionQuestion,
				"It's a lion.",
				AnswerResult.CORRECT,
				null
			),
			new LiveCase(
				"동물 식별 문법 오류",
				lionQuestion,
				"It are a lion.",
				AnswerResult.PARTIALLY_CORRECT,
				null
			),
			new LiveCase(
				"동물 식별 의미 오답",
				lionQuestion,
				"It is a zebra.",
				AnswerResult.INCORRECT,
				29
			)
		);
	}

	private Question question(
		LearningTopic topic,
		QuestionType type,
		String questionText,
		String questionTextKo,
		String modelAnswer,
		Set<String> acceptedAnswers
	) {
		return Question.create(
			topic,
			Difficulty.EASY,
			type,
			questionText,
			questionTextKo,
			null,
			modelAnswer,
			acceptedAnswers,
			null,
			null
		);
	}

	private AnswerAssessmentInput assessmentInput(
		Question question,
		String answerText
	) {
		return new AnswerAssessmentInput(
			question.getQuestionText(),
			question.getQuestionTextKo(),
			question.getModelAnswer(),
			question.getAcceptedAnswers(),
			answerText
		);
	}

	private boolean containsSttFormattingAdvice(String feedbackText) {
		return List.of(
			"대문자",
			"소문자",
			"문장부호",
			"마침표",
			"쉼표",
			"물음표",
			"느낌표",
			"아포스트로피"
		).stream().anyMatch(feedbackText::contains);
	}

	private record LiveCase(
		String name,
		Question question,
		String answerText,
		AnswerResult expectedResult,
		Integer maximumMeaningScore
	) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SpeechTestConfiguration {

		@Bean
		SpeechTranscriber speechTranscriber() {
			return mock(SpeechTranscriber.class);
		}
	}
}
