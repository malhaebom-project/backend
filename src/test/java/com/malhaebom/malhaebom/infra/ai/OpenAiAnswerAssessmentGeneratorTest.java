package com.malhaebom.malhaebom.infra.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;

class OpenAiAnswerAssessmentGeneratorTest {

	@Test
	void 하나의_AI_응답에서_채점과_피드백을_구조화해서_반환한다() {
		CapturingChatModel chatModel = new CapturingChatModel("""
			{
			  "recognized": true,
			  "meaningScore": 48,
			  "expressionScore": 27,
			  "grammarScore": 18,
			  "feedbackText": "현재진행형을 자연스럽게 잘 사용했어요!"
			}
			""");
		OpenAiAnswerAssessmentGenerator generator =
			new OpenAiAnswerAssessmentGenerator(
				ChatClient.builder(chatModel)
			);

		AnswerAssessment assessment = generator
			.generateAsync(assessmentInput("He is running."))
			.toCompletableFuture()
			.join();

		assertEquals(93, assessment.totalScore());
		assertEquals(AnswerResult.CORRECT, assessment.result());
		assertTrue(chatModel.prompt().getContents().contains(
			"학습자 답변: He is running."
		));
	}

	@Test
	void 빈_답변은_AI를_호출하기_전에_거부한다() {
		CapturingChatModel chatModel = new CapturingChatModel("{}");
		OpenAiAnswerAssessmentGenerator generator =
			new OpenAiAnswerAssessmentGenerator(
				ChatClient.builder(chatModel)
			);

		assertThrows(
			IllegalArgumentException.class,
			() -> generator.generateAsync(assessmentInput(" "))
		);
		assertNull(chatModel.prompt());
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

	private static final class CapturingChatModel implements ChatModel {

		private final String response;
		private Prompt prompt;

		private CapturingChatModel(String response) {
			this.response = response;
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			this.prompt = prompt;
			return new ChatResponse(List.of(
				new Generation(new AssistantMessage(response))
			));
		}

		private Prompt prompt() {
			return prompt;
		}
	}
}
