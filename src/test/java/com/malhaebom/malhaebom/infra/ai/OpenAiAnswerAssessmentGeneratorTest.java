package com.malhaebom.malhaebom.infra.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.openai.client.OpenAIClientAsync;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import com.openai.services.async.ChatServiceAsync;
import com.openai.services.async.chat.ChatCompletionServiceAsync;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.observability.MicrometerAnswerAssessmentMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.MicrometerOpenAiAnswerAssessmentMetricsRecorder;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;

class OpenAiAnswerAssessmentGeneratorTest {

	@Test
	void 비동기_HTTP_응답에서_채점과_피드백을_구조화해서_반환한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		OpenAiAnswerAssessmentGenerator generator = generator(fixture.client());

		CompletableFuture<AnswerAssessment> assessmentFuture = generator
			.generateAsync(assessmentInput("He is running."))
			.result()
			.toCompletableFuture();

		assertFalse(assessmentFuture.isDone());
		fixture.response().complete(chatCompletion());
		AnswerAssessment assessment = assessmentFuture.join();

		assertEquals(93, assessment.totalScore());
		assertEquals(AnswerResult.CORRECT, assessment.result());
		ChatCompletionCreateParams params =
			capturedParams(fixture.completions());
		assertEquals("gpt-5-mini", params.model().asString());
		assertEquals(
			200L,
			params.maxCompletionTokens().orElseThrow()
		);
		assertTrue(params.responseFormat().isPresent());
		assertTrue(params.toString().contains("난이도: EASY"));
		assertTrue(params.toString().contains("문제 유형: PICTURE_DESCRIPTION"));
		assertTrue(params.toString().contains(
			"채점 참고 상황:\n소년이 공원에서 달리고 있다."
		));
		assertTrue(params.toString().contains("학습자 답변: He is running."));
	}

	@Test
	void OpenAI_응답의_청구_토큰을_종류별로_한_번_기록한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		OpenAiAnswerAssessmentGenerator generator = generator(
			fixture.client(),
			meterRegistry
		);

		CompletableFuture<AnswerAssessment> assessmentFuture = generator
			.generateAsync(assessmentInput("He is running."))
			.result()
			.toCompletableFuture();
		fixture.response().complete(chatCompletion(completionUsage()));
		assessmentFuture.join();

		assertTokenCount(meterRegistry, "prompt", 120);
		assertTokenCount(meterRegistry, "completion", 30);
		assertTokenCount(meterRegistry, "total", 150);
		assertTokenCount(meterRegistry, "cached", 40);
		assertTokenCount(meterRegistry, "reasoning", 12);
	}

	@Test
	void 난이도에_맞는_채점_기준만_system_message에_포함한다() {
		AsyncClientFixture easyFixture = asyncClientFixture();
		generator(easyFixture.client()).generateAsync(
			assessmentInput(Difficulty.EASY, "Tiger")
		);
		String easyParams = capturedParams(easyFixture.completions()).toString();

		AsyncClientFixture normalFixture = asyncClientFixture();
		generator(normalFixture.client()).generateAsync(
			assessmentInput(Difficulty.NORMAL, "Tiger")
		);
		String normalParams = capturedParams(
			normalFixture.completions()
		).toString();

		AsyncClientFixture hardFixture = asyncClientFixture();
		generator(hardFixture.client()).generateAsync(
			assessmentInput(Difficulty.HARD, "Tiger")
		);
		String hardParams = capturedParams(hardFixture.completions()).toString();

		assertTrue(easyParams.contains("[현재 난이도: EASY]"));
		assertTrue(easyParams.contains("한 단어, 짧은 구, Yes/No도"));
		assertTrue(easyParams.contains(
			"가장 낮은 상한을 적용"
		));
		assertTrue(easyParams.contains(
			"CORRECT: meaningScore가 40점 이상이고 총점이 80점 이상"
		));
		assertFalse(easyParams.contains("[현재 난이도: HARD]"));
		assertTrue(normalParams.contains("[현재 난이도: NORMAL]"));
		assertTrue(normalParams.contains("expressionScore 상한: 12점"));
		assertTrue(normalParams.contains("grammarScore 상한: 8점"));
		assertTrue(normalParams.contains("34점입니다"));
		assertTrue(hardParams.contains("[현재 난이도: HARD]"));
		assertTrue(hardParams.contains("expressionScore 상한: 8점"));
		assertTrue(hardParams.contains("grammarScore 상한: 5점"));
		assertFalse(hardParams.contains("[현재 난이도: EASY]"));
	}

	@Test
	void 비동기_HTTP_실패는_예외_완료로_전달한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		OpenAiAnswerAssessmentGenerator generator = generator(
			fixture.client(),
			meterRegistry
		);
		RuntimeException failure = new OpenAIIoException(
			"OpenAI timeout",
			new SocketTimeoutException("read timed out")
		);

		CompletableFuture<AnswerAssessment> assessmentFuture = generator
			.generateAsync(assessmentInput("He is running."))
			.result()
			.toCompletableFuture();
		fixture.response().completeExceptionally(failure);

		CompletionException exception = assertThrows(
			CompletionException.class,
			assessmentFuture::join
		);
		assertSame(failure, exception.getCause());
		assertEquals(
			1.0,
			meterRegistry.get(
				"malhaebom.openai.answer.assessment.failures"
			).tag("reason", "timeout").counter().count()
		);
	}

	@Test
	void OpenAI가_응답을_거부하면_토큰과_거부_원인을_함께_기록한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		OpenAiAnswerAssessmentGenerator generator = generator(
			fixture.client(),
			meterRegistry
		);
		ChatCompletion completion = chatCompletion(completionUsage());
		when(completion.choices().getFirst().message().refusal())
			.thenReturn(Optional.of("safety refusal"));

		CompletableFuture<AnswerAssessment> assessmentFuture = generator
			.generateAsync(assessmentInput("He is running."))
			.result()
			.toCompletableFuture();
		fixture.response().complete(completion);

		assertThrows(CompletionException.class, assessmentFuture::join);
		assertTokenCount(meterRegistry, "total", 150);
		assertEquals(
			1.0,
			meterRegistry.get(
				"malhaebom.openai.answer.assessment.failures"
			).tag("reason", "refusal").counter().count()
		);
	}

	@Test
	void OpenAI_429_실패는_rate_limit으로_분류한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		OpenAiAnswerAssessmentGenerator generator = generator(
			fixture.client(),
			meterRegistry
		);
		RateLimitException failure = RateLimitException.builder()
			.headers(Headers.builder().build())
			.build();

		CompletableFuture<AnswerAssessment> assessmentFuture = generator
			.generateAsync(assessmentInput("He is running."))
			.result()
			.toCompletableFuture();
		fixture.response().completeExceptionally(failure);

		assertThrows(CompletionException.class, assessmentFuture::join);
		assertEquals(
			1.0,
			meterRegistry.get(
				"malhaebom.openai.answer.assessment.failures"
			).tag("reason", "rate_limit").counter().count()
		);
	}

	@Test
	void 구조화_응답_파싱_실패도_토큰과_invalid_response를_한_번_기록한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
		OpenAiAnswerAssessmentGenerator generator = generator(
			fixture.client(),
			meterRegistry
		);
		ChatCompletion completion = chatCompletion(completionUsage());
		when(completion.choices().getFirst().message().content())
			.thenReturn(Optional.of("not-json"));

		CompletableFuture<AnswerAssessment> assessmentFuture = generator
			.generateAsync(assessmentInput("He is running."))
			.result()
			.toCompletableFuture();
		fixture.response().complete(completion);

		assertThrows(CompletionException.class, assessmentFuture::join);
		assertTokenCount(meterRegistry, "total", 150);
		assertEquals(
			1.0,
			meterRegistry.get(
				"malhaebom.openai.answer.assessment.failures"
			).tag("reason", "invalid_response").counter().count()
		);
	}

	@Test
	void 채점_작업을_취소하면_OpenAI_HTTP_요청에_취소를_전파한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		OpenAiAnswerAssessmentGenerator generator = generator(
			fixture.client(),
			0
		);
		AnswerAssessmentTask task = generator.generateAsync(
			assessmentInput("He is running.")
		);

		assertTrue(task.cancel());
		assertTrue(fixture.response().isCancelled());
		assertTrue(task.result().toCompletableFuture().isCompletedExceptionally());

		generator.generateAsync(assessmentInput("He is running."));
		verify(fixture.completions(), times(2)).create(
			any(ChatCompletionCreateParams.class)
		);
	}

	private AsyncClientFixture asyncClientFixture() {
		OpenAIClientAsync client = mock(OpenAIClientAsync.class);
		ChatServiceAsync chat = mock(ChatServiceAsync.class);
		ChatCompletionServiceAsync completions =
			mock(ChatCompletionServiceAsync.class);
		CompletableFuture<ChatCompletion> response =
			new CompletableFuture<>();
		when(client.chat()).thenReturn(chat);
		when(chat.completions()).thenReturn(completions);
		when(completions.create(
			any(ChatCompletionCreateParams.class)
		)).thenReturn(response);
		return new AsyncClientFixture(client, completions, response);
	}

	private OpenAiAnswerAssessmentGenerator generator(
		OpenAIClientAsync client
	) {
		return generator(client, 0);
	}

	private OpenAiAnswerAssessmentGenerator generator(
		OpenAIClientAsync client,
		SimpleMeterRegistry meterRegistry
	) {
		return generator(client, 0, meterRegistry);
	}

	private OpenAiAnswerAssessmentGenerator generator(
		OpenAIClientAsync client,
		int queueCapacity
	) {
		return generator(client, queueCapacity, new SimpleMeterRegistry());
	}

	private OpenAiAnswerAssessmentGenerator generator(
		OpenAIClientAsync client,
		int queueCapacity,
		SimpleMeterRegistry meterRegistry
	) {
		return new OpenAiAnswerAssessmentGenerator(
			client,
			properties(),
			new AnswerAssessmentRateLimitQueue(
				new AnswerAssessmentQueueProperties(
					queueCapacity,
					queueCapacity == 0
						? Duration.ZERO
						: Duration.ofSeconds(10)
				),
				new MicrometerAnswerAssessmentMetricsRecorder(
					meterRegistry
				),
				(task, delay) -> () -> {
				},
				System::nanoTime
			),
			new MicrometerOpenAiAnswerAssessmentMetricsRecorder(
				meterRegistry
			)
		);
	}

	private ChatCompletion chatCompletion() {
		return chatCompletion(null);
	}

	private ChatCompletion chatCompletion(CompletionUsage usage) {
		ChatCompletion completion = mock(ChatCompletion.class);
		ChatCompletion.Choice choice = mock(ChatCompletion.Choice.class);
		ChatCompletionMessage message = mock(ChatCompletionMessage.class);
		when(completion.choices()).thenReturn(List.of(choice));
		when(choice.message()).thenReturn(message);
		when(message.refusal()).thenReturn(Optional.empty());
		when(message.content()).thenReturn(Optional.of("""
			{
			  "recognized": true,
			  "meaningScore": 48,
			  "expressionScore": 27,
			  "grammarScore": 18,
			  "feedbackText": "현재진행형을 자연스럽게 잘 사용했어요!"
			}
			"""));
		when(completion.usage()).thenReturn(Optional.ofNullable(usage));
		return completion;
	}

	private CompletionUsage completionUsage() {
		return CompletionUsage.builder()
			.promptTokens(120)
			.completionTokens(30)
			.totalTokens(150)
			.promptTokensDetails(
				CompletionUsage.PromptTokensDetails.builder()
					.cachedTokens(40)
					.build()
			)
			.completionTokensDetails(
				CompletionUsage.CompletionTokensDetails.builder()
					.reasoningTokens(12)
					.build()
			)
			.build();
	}

	private void assertTokenCount(
		SimpleMeterRegistry meterRegistry,
		String type,
		double expected
	) {
		assertEquals(
			expected,
			meterRegistry.get(
				"malhaebom.openai.answer.assessment.tokens"
			).tag("type", type).counter().count()
		);
	}

	private ChatCompletionCreateParams capturedParams(
		ChatCompletionServiceAsync completions
	) {
		ArgumentCaptor<ChatCompletionCreateParams> captor =
			ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
		verify(completions).create(captor.capture());
		return captor.getValue();
	}

	private OpenAiAnswerAssessmentProperties properties() {
		OpenAiAnswerAssessmentProperties properties =
			new OpenAiAnswerAssessmentProperties();
		properties.getChat().setModel("gpt-5-mini");
		properties.getChat().setReasoningEffort("low");
		properties.getChat().setVerbosity("low");
		properties.getChat().setMaxCompletionTokens(200L);
		return properties;
	}

	private AnswerAssessmentInput assessmentInput(String answerText) {
		return assessmentInput(Difficulty.EASY, answerText);
	}

	private AnswerAssessmentInput assessmentInput(
		Difficulty difficulty,
		String answerText
	) {
		Question question = createQuestion(difficulty);
		return new AnswerAssessmentInput(
			question.getDifficulty(),
			question.getType(),
			question.getQuestionText(),
			question.getQuestionTextKo(),
			question.getGradingContext(),
			question.getModelAnswer(),
			question.getAcceptedAnswers(),
			answerText
		);
	}

	private Question createQuestion(Difficulty difficulty) {
		return Question.create(
			LearningTopic.DAILY_LIFE,
			difficulty,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"소년은 무엇을 하고 있나요?",
			null,
			"소년이 공원에서 달리고 있다.",
			"The boy is running.",
			Set.of("He is running."),
			null,
			null
		);
	}

	private record AsyncClientFixture(
		OpenAIClientAsync client,
		ChatCompletionServiceAsync completions,
		CompletableFuture<ChatCompletion> response
	) {
	}
}
