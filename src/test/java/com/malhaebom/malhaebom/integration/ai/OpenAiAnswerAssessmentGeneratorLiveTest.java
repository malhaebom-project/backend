package com.malhaebom.malhaebom.integration.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.infra.ai.OpenAiAnswerAssessmentGenerator;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

/**
 * 실제 OpenAI 답변 평가 테스트입니다.
 *
 * <p>{@code config/application.yaml}에 {@code OPENAI_API_KEY}를 설정하고
 * {@code ./gradlew liveTest}로 실행합니다. 기본 {@code test}에서는 실행되지
 * 않습니다.
 */
@Tag("live")
@SpringBootTest(
	properties = "spring.ai.openai.api-key=${OPENAI_API_KEY}"
)
@ActiveProfiles("test")
@Import(OpenAiAnswerAssessmentGeneratorLiveTest.SpeechTestConfiguration.class)
class OpenAiAnswerAssessmentGeneratorLiveTest {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String CASES_RESOURCE =
		"answer-assessment-live-cases.json";
	private static final List<String> STT_FORMATTING_ADVICE_TERMS = List.of(
		"대문자",
		"소문자",
		"문장부호",
		"마침표",
		"쉼표",
		"물음표",
		"느낌표",
		"아포스트로피"
	);

	@Autowired
	private OpenAiAnswerAssessmentGenerator generator;

	@Test
	void JSON_케이스로_실제_AI_답변_평가를_검증한다() throws IOException {
		assertAll(liveCases().stream()
			.map(liveCase -> (Executable)() -> assertLiveCase(liveCase)));
	}

	private void assertLiveCase(LiveCase liveCase) {
		AnswerAssessment assessment = generator
			.generateAsync(liveCase.toInput())
			.result()
			.toCompletableFuture()
			.join();

		assertEquals(
			liveCase.expectedResult(),
			assessment.result(),
			() -> liveCase.name() + ": " + assessment
		);
		assertScoreBounds(liveCase, assessment);
		assertFeedback(liveCase, assessment);
	}

	private List<LiveCase> liveCases() throws IOException {
		try (InputStream inputStream = getClass()
			.getClassLoader()
			.getResourceAsStream(CASES_RESOURCE)) {
			assertNotNull(
				inputStream,
				"AI 답변 평가 케이스 JSON을 찾을 수 없습니다: " + CASES_RESOURCE
			);
			return Arrays.asList(
				OBJECT_MAPPER.readValue(inputStream, LiveCase[].class)
			);
		}
	}

	private void assertScoreBounds(
		LiveCase liveCase,
		AnswerAssessment assessment
	) {
		assertMinimum(
			liveCase.minimumMeaningScore(),
			assessment.meaningScore(),
			liveCase.name(),
			"meaningScore"
		);
		assertMaximum(
			liveCase.maximumMeaningScore(),
			assessment.meaningScore(),
			liveCase.name(),
			"meaningScore"
		);
		assertMinimum(
			liveCase.minimumExpressionScore(),
			assessment.expressionScore(),
			liveCase.name(),
			"expressionScore"
		);
		assertMaximum(
			liveCase.maximumExpressionScore(),
			assessment.expressionScore(),
			liveCase.name(),
			"expressionScore"
		);
		assertMinimum(
			liveCase.minimumGrammarScore(),
			assessment.grammarScore(),
			liveCase.name(),
			"grammarScore"
		);
		assertMaximum(
			liveCase.maximumGrammarScore(),
			assessment.grammarScore(),
			liveCase.name(),
			"grammarScore"
		);
		assertMinimum(
			liveCase.minimumTotalScore(),
			assessment.totalScore(),
			liveCase.name(),
			"totalScore"
		);
		assertMaximum(
			liveCase.maximumTotalScore(),
			assessment.totalScore(),
			liveCase.name(),
			"totalScore"
		);
	}

	private void assertMinimum(
		Integer expectedMinimum,
		int actual,
		String caseName,
		String scoreName
	) {
		if (expectedMinimum == null) {
			return;
		}
		assertTrue(
			actual >= expectedMinimum,
			() -> "%s: %s expected >= %d but was %d".formatted(
				caseName,
				scoreName,
				expectedMinimum,
				actual
			)
		);
	}

	private void assertMaximum(
		Integer expectedMaximum,
		int actual,
		String caseName,
		String scoreName
	) {
		if (expectedMaximum == null) {
			return;
		}
		assertTrue(
			actual <= expectedMaximum,
			() -> "%s: %s expected <= %d but was %d".formatted(
				caseName,
				scoreName,
				expectedMaximum,
				actual
			)
		);
	}

	private void assertFeedback(
		LiveCase liveCase,
		AnswerAssessment assessment
	) {
		if (!liveCase.forbidSttFormattingAdvice()) {
			return;
		}
		assertTrue(
			STT_FORMATTING_ADVICE_TERMS.stream()
				.noneMatch(assessment.feedbackText()::contains),
			() -> liveCase.name() + ": " + assessment.feedbackText()
		);
	}

	private record LiveCase(
		String name,
		String questionText,
		String questionTextKo,
		String modelAnswer,
		List<String> acceptedAnswers,
		String answerText,
		AnswerResult expectedResult,
		Integer minimumMeaningScore,
		Integer maximumMeaningScore,
		Integer minimumExpressionScore,
		Integer maximumExpressionScore,
		Integer minimumGrammarScore,
		Integer maximumGrammarScore,
		Integer minimumTotalScore,
		Integer maximumTotalScore,
		boolean forbidSttFormattingAdvice
	) {

		public LiveCase {
			acceptedAnswers = acceptedAnswers == null
				? List.of()
				: List.copyOf(acceptedAnswers);
		}

		AnswerAssessmentInput toInput() {
			return new AnswerAssessmentInput(
				questionText,
				questionTextKo,
				modelAnswer,
				new LinkedHashSet<>(acceptedAnswers),
				answerText
			);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SpeechTestConfiguration {

		@Bean
		SpeechTranscriber speechTranscriber() {
			return mock(SpeechTranscriber.class);
		}
	}
}
