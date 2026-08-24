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

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.openai.client.OpenAIClientAsync;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.services.async.ChatServiceAsync;
import com.openai.services.async.chat.ChatCompletionServiceAsync;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.mockito.ArgumentCaptor;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.observability.MicrometerAnswerAssessmentMetricsRecorder;
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
		OpenAiAnswerAssessmentGenerator generator = generator(fixture.client());
		RuntimeException failure = new IllegalStateException("OpenAI timeout");

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
	}

	@Test
	void active와_queue가_차면_그_다음_요청은_OpenAI_호출_없이_거절한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		OpenAiAnswerAssessmentGenerator generator = generator(
			fixture.client(),
			1,
			1
		);

		CompletableFuture<AnswerAssessment> first = generator
			.generateAsync(assessmentInput("He is running."))
			.result()
			.toCompletableFuture();
		CompletableFuture<AnswerAssessment> queued = generator
			.generateAsync(assessmentInput("He is running."))
			.result()
			.toCompletableFuture();
		CompletableFuture<AnswerAssessment> rejected = generator
			.generateAsync(assessmentInput("He is running."))
			.result()
			.toCompletableFuture();

		CompletionException exception = assertThrows(
			CompletionException.class,
			rejected::join
		);
		ApiException cause = assertInstanceOf(
			ApiException.class,
			exception.getCause()
		);
		assertEquals(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED,
			cause.getErrorCode()
		);
		verify(fixture.completions(), times(1)).create(
			any(ChatCompletionCreateParams.class)
		);

		fixture.response().complete(chatCompletion());
		first.join();
		queued.join();
		verify(fixture.completions(), times(2)).create(
			any(ChatCompletionCreateParams.class)
		);
	}

	@Test
	void queued_작업_취소는_OpenAI를_호출하지_않고_대기열에서_제거한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		OpenAiAnswerAssessmentGenerator generator = generator(
			fixture.client(),
			1,
			1
		);
		AnswerAssessmentTask first = generator.generateAsync(
			assessmentInput("He is running.")
		);
		AnswerAssessmentTask queued = generator.generateAsync(
			assessmentInput("He is running.")
		);

		assertTrue(queued.cancel());
		verify(fixture.completions(), times(1)).create(
			any(ChatCompletionCreateParams.class)
		);

		fixture.response().complete(chatCompletion());
		first.result().toCompletableFuture().join();
		verify(fixture.completions(), times(1)).create(
			any(ChatCompletionCreateParams.class)
		);
	}

	@Test
	void 채점_작업을_취소하면_OpenAI_HTTP_요청을_취소하고_자리를_반환한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		OpenAiAnswerAssessmentGenerator generator = generator(
			fixture.client(),
			1
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
		return generator(client, 32);
	}

	private OpenAiAnswerAssessmentGenerator generator(
		OpenAIClientAsync client,
		int maxConcurrentRequests
	) {
		return generator(client, maxConcurrentRequests, 0);
	}

	private OpenAiAnswerAssessmentGenerator generator(
		OpenAIClientAsync client,
		int maxConcurrentRequests,
		int queueCapacity
	) {
		return new OpenAiAnswerAssessmentGenerator(
			client,
			properties(),
			new AnswerAssessmentConcurrencyLimiter(
				new AnswerAssessmentConcurrencyProperties(
					maxConcurrentRequests,
					queueCapacity,
					queueCapacity == 0
						? Duration.ZERO
						: Duration.ofSeconds(10)
				),
				new MicrometerAnswerAssessmentMetricsRecorder(
					new SimpleMeterRegistry()
				),
				(task, delay) -> () -> {
				},
				System::nanoTime
			)
		);
	}

	private ChatCompletion chatCompletion() {
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
		return completion;
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
