package com.malhaebom.malhaebom.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;

class QuestionTtsResponseTest {

	@Test
	void 문제의_음성_응답을_생성한다() {
		Question question = Question.create(
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			null,
			"He is running.",
			Set.of("He is running"),
			null,
			"https://cdn.example.com/tts/question-501.mp3"
		);
		ReflectionTestUtils.setField(question, "id", 501L);

		QuestionTtsResponse response = QuestionTtsResponse.from(question);

		assertThat(response.questionId()).isEqualTo(501L);
		assertThat(response.text()).isEqualTo("What is the boy doing?");
		assertThat(response.audioUrl())
			.isEqualTo("https://cdn.example.com/tts/question-501.mp3");
	}
}
