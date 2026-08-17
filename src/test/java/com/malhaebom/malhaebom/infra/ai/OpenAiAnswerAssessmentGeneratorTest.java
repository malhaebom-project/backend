package com.malhaebom.malhaebom.infra.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.mockito.ArgumentCaptor;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;

class OpenAiAnswerAssessmentGeneratorTest {

	@Test
	void 비동기_HTTP_응답에서_채점과_피드백을_구조화해서_반환한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		OpenAiAnswerAssessmentGenerator generator =
			new OpenAiAnswerAssessmentGenerator(
				fixture.client(),
				properties()
			);

		CompletableFuture<AnswerAssessment> assessmentFuture = generator
			.generateAsync(assessmentInput("He is running."))
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
		assertTrue(params.toString().contains("학습자 답변: He is running."));
	}

	@Test
	void 비동기_HTTP_실패는_예외_완료로_전달한다() {
		AsyncClientFixture fixture = asyncClientFixture();
		OpenAiAnswerAssessmentGenerator generator =
			new OpenAiAnswerAssessmentGenerator(
				fixture.client(),
				properties()
			);
		RuntimeException failure = new IllegalStateException("OpenAI timeout");

		CompletableFuture<AnswerAssessment> assessmentFuture = generator
			.generateAsync(assessmentInput("He is running."))
			.toCompletableFuture();
		fixture.response().completeExceptionally(failure);

		CompletionException exception = assertThrows(
			CompletionException.class,
			assessmentFuture::join
		);
		assertSame(failure, exception.getCause());
	}

	@Test
	void 빈_답변은_AI를_호출하기_전에_거부한다() {
		OpenAIClientAsync client = mock(OpenAIClientAsync.class);
		OpenAiAnswerAssessmentGenerator generator =
			new OpenAiAnswerAssessmentGenerator(client, properties());

		assertThrows(
			IllegalArgumentException.class,
			() -> generator.generateAsync(assessmentInput(" "))
		);
		verify(client, never()).chat();
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
		Question question = createQuestion();
		return new AnswerAssessmentInput(
			question.getQuestionText(),
			question.getQuestionTextKo(),
			question.getModelAnswer(),
			question.getAcceptedAnswers(),
			answerText
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

	private record AsyncClientFixture(
		OpenAIClientAsync client,
		ChatCompletionServiceAsync completions,
		CompletableFuture<ChatCompletion> response
	) {
	}
}
