package com.malhaebom.malhaebom.integration.learning;

import java.util.List;
import java.util.Set;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;

final class LearningJpaTestFixture {

	private LearningJpaTestFixture() {
	}

	static LearningSession saveSession(
		QuestionRepository questionRepository,
		LearningSessionRepository sessionRepository,
		String hintText
	) {
		Question question = questionRepository.saveAndFlush(
			Question.create(
				LearningTopic.DAILY_LIFE,
				Difficulty.EASY,
				QuestionType.PICTURE_DESCRIPTION,
				"What is the boy doing?",
				"남자아이는 무엇을 하고 있나요?",
				null,
				"",
				"The boy is running.",
				Set.of("He is running.", "He's running."),
				hintText,
				null
			)
		);
		return sessionRepository.saveAndFlush(
			LearningSession.create(
				1L,
				LearningTopic.DAILY_LIFE,
				Difficulty.EASY,
				List.of(question)
			)
		);
	}
}
