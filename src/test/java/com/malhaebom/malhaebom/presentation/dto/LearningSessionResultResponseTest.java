package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.learning.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static com.malhaebom.malhaebom.support.LearningSessionTestActions.completeCurrentQuestion;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LearningSessionResultResponseTest {
	@Test
	void 완료_응답은_기록된_시작과_완료_시각으로_학습_시간을_계산한다() {
		LearningSession session = LearningSession.create(
			1L,
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			List.of(createQuestion(), createQuestion(), createQuestion())
		);
		completeCurrentQuestion(session, true);
		completeCurrentQuestion(session, false);
		completeCurrentQuestion(session, true);
		LocalDateTime startedAt = LocalDateTime.of(2026, 8, 27, 1, 0);
		LocalDateTime completedAt = startedAt.plusMinutes(5).plusSeconds(12);
		ReflectionTestUtils.setField(session, "startedAt", startedAt);
		ReflectionTestUtils.setField(session, "completedAt", completedAt);

		LearningSessionResultResponse response =
			LearningSessionResultResponse.from(session);

		assertEquals(67, response.correctRate());
		assertEquals(312, response.studySeconds());
		assertEquals(
			completedAt.atOffset(ZoneOffset.UTC),
			response.completedAt()
		);
	}

	private Question createQuestion() {
		return Question.create(
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			QuestionType.SHORT_ANSWER,
			"What is this?",
			"이것은 무엇인가요?",
			null,
			"",
			"answer",
			Set.of("answer"),
			null,
			null
		);
	}
}
