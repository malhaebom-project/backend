package com.malhaebom.malhaebom.presentation.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;

class LearningSessionResponseTest {

	@Test
	void 완료_응답에_정답률_학습시간_완료시각을_포함한다() {
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			List.of(createQuestion(), createQuestion(), createQuestion())
		);
		session.completeCurrentQuestion(true);
		session.completeCurrentQuestion(false);
		session.completeCurrentQuestion(true);

		LearningSessionResponse response = LearningSessionResponse.from(session);

		assertEquals(67, response.correctRate());
		assertEquals(
			Duration.between(
				session.getStartedAt(),
				session.getCompletedAt()
			).getSeconds(),
			response.studySeconds()
		);
		assertEquals(session.getCompletedAt(), response.completedAt());
	}

	private Question createQuestion() {
		return Question.create(
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			QuestionType.SHORT_ANSWER,
			"What is this?",
			"이것은 무엇인가요?",
			null,
			"answer",
			Set.of("answer"),
			null,
			null
		);
	}
}
