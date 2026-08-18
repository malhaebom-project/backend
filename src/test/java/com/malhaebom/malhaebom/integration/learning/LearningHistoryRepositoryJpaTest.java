package com.malhaebom.malhaebom.integration.learning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningSessionStatus;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;

@DataJpaTest
@Import(JpaAuditingConfiguration.class)
class LearningHistoryRepositoryJpaTest {

	private static final Long CHILD_ID = 10L;
	private static final Long OTHER_CHILD_ID = 20L;

	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private LearningSessionRepository learningSessionRepository;

	private Question firstQuestion;
	private Question secondQuestion;

	@BeforeEach
	void setUp() {
		firstQuestion = questionRepository.saveAndFlush(Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.SHORT_ANSWER,
			"What is this?",
			"이것은 무엇인가요?",
			null,
			"It is a book.",
			Set.of("It is a book."),
			null,
			null
		));
		secondQuestion = questionRepository.saveAndFlush(Question.create(
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			QuestionType.PICTURE_DESCRIPTION,
			"What is the boy doing?",
			"남자아이는 무엇을 하고 있나요?",
			null,
			"The boy is running.",
			Set.of("He is running."),
			null,
			null
		));
	}

	@Test
	void 완료된_어린이_학습만_최신순으로_페이징한다() {
		LocalDateTime olderCompletedAt =
			LocalDateTime.of(2026, 7, 21, 10, 0);
		LocalDateTime newerCompletedAt =
			LocalDateTime.of(2026, 7, 22, 14, 5);
		LearningSession older = saveCompletedSession(
			CHILD_ID,
			LearningTopic.ANIMAL,
			List.of(firstQuestion),
			List.of(true),
			olderCompletedAt
		);
		LearningSession newer = saveCompletedSession(
			CHILD_ID,
			LearningTopic.DAILY_LIFE,
			List.of(firstQuestion, secondQuestion),
			List.of(true, false),
			newerCompletedAt
		);
		saveInProgressSession(CHILD_ID);
		saveCompletedSession(
			OTHER_CHILD_ID,
			LearningTopic.FOOD,
			List.of(firstQuestion),
			List.of(true),
			newerCompletedAt.plusHours(1)
		);

		var firstPage = learningSessionRepository.findLearningHistory(
			CHILD_ID,
			LearningSessionStatus.COMPLETED,
			LocalDateTime.of(2026, 7, 1, 0, 0),
			LocalDateTime.of(2026, 8, 1, 0, 0),
			PageRequest.of(0, 1)
		);
		var secondPage = learningSessionRepository.findLearningHistory(
			CHILD_ID,
			LearningSessionStatus.COMPLETED,
			LocalDateTime.of(2026, 7, 1, 0, 0),
			LocalDateTime.of(2026, 8, 1, 0, 0),
			PageRequest.of(1, 1)
		);

		assertThat(firstPage.getTotalElements()).isEqualTo(2);
		assertThat(firstPage.getTotalPages()).isEqualTo(2);
		assertThat(firstPage.getContent()).singleElement().satisfies(item -> {
			assertThat(item.getSessionId()).isEqualTo(newer.getId());
			assertThat(item.getTopic()).isEqualTo(LearningTopic.DAILY_LIFE);
			assertThat(item.getQuestionCount()).isEqualTo(2);
			assertThat(item.getCorrectCount()).isEqualTo(1);
			assertThat(item.getCompletedAt()).isEqualTo(newerCompletedAt);
		});
		assertThat(secondPage.getContent()).singleElement().satisfies(item -> {
			assertThat(item.getSessionId()).isEqualTo(older.getId());
			assertThat(item.getQuestionCount()).isEqualTo(1);
			assertThat(item.getCorrectCount()).isEqualTo(1);
		});
	}

	@Test
	void 완료일_조회_범위의_끝은_포함하지_않는다() {
		saveCompletedSession(
			CHILD_ID,
			LearningTopic.ANIMAL,
			List.of(firstQuestion),
			List.of(true),
			LocalDateTime.of(2026, 7, 31, 23, 59)
		);
		saveCompletedSession(
			CHILD_ID,
			LearningTopic.FOOD,
			List.of(firstQuestion),
			List.of(true),
			LocalDateTime.of(2026, 8, 1, 0, 0)
		);

		var history = learningSessionRepository.findLearningHistory(
			CHILD_ID,
			LearningSessionStatus.COMPLETED,
			LocalDateTime.of(2026, 7, 1, 0, 0),
			LocalDateTime.of(2026, 8, 1, 0, 0),
			PageRequest.of(0, 10)
		);

		assertThat(history.getTotalElements()).isEqualTo(1);
		assertThat(history.getContent()).singleElement()
			.extracting(item -> item.getTopic())
			.isEqualTo(LearningTopic.ANIMAL);
	}

	@Test
	void 같은_완료_시각은_세션_ID_역순으로_페이징한다() {
		LocalDateTime completedAt = LocalDateTime.of(2026, 7, 22, 14, 5);
		LearningSession first = saveCompletedSession(
			CHILD_ID,
			LearningTopic.ANIMAL,
			List.of(firstQuestion),
			List.of(true),
			completedAt
		);
		LearningSession second = saveCompletedSession(
			CHILD_ID,
			LearningTopic.FOOD,
			List.of(secondQuestion),
			List.of(false),
			completedAt
		);

		var firstPage = learningSessionRepository.findLearningHistory(
			CHILD_ID,
			LearningSessionStatus.COMPLETED,
			completedAt.minusDays(1),
			completedAt.plusDays(1),
			PageRequest.of(0, 1)
		);
		var secondPage = learningSessionRepository.findLearningHistory(
			CHILD_ID,
			LearningSessionStatus.COMPLETED,
			completedAt.minusDays(1),
			completedAt.plusDays(1),
			PageRequest.of(1, 1)
		);

		assertThat(firstPage.getContent()).singleElement()
			.extracting(item -> item.getSessionId())
			.isEqualTo(second.getId());
		assertThat(secondPage.getContent()).singleElement()
			.extracting(item -> item.getSessionId())
			.isEqualTo(first.getId());
	}

	@Test
	void 비활성화된_문제의_학습_기록도_조회한다() {
		LocalDateTime completedAt = LocalDateTime.of(2026, 7, 22, 14, 5);
		LearningSession session = saveCompletedSession(
			CHILD_ID,
			LearningTopic.ANIMAL,
			List.of(firstQuestion),
			List.of(true),
			completedAt
		);
		firstQuestion.deactivate();
		questionRepository.flush();

		var history = learningSessionRepository.findLearningHistory(
			CHILD_ID,
			LearningSessionStatus.COMPLETED,
			completedAt.minusDays(1),
			completedAt.plusDays(1),
			PageRequest.of(0, 10)
		);

		assertThat(history.getContent()).singleElement()
			.extracting(item -> item.getSessionId())
			.isEqualTo(session.getId());
	}

	@Test
	void 완료된_학습의_전체와_주제별_통계를_조회한다() {
		LocalDateTime animalCompletedAt =
			LocalDateTime.of(2026, 8, 17, 10, 0);
		LocalDateTime secondAnimalCompletedAt =
			LocalDateTime.of(2026, 8, 18, 9, 0);
		LocalDateTime foodCompletedAt =
			LocalDateTime.of(2026, 8, 18, 11, 0);
		saveCompletedSession(
			CHILD_ID,
			LearningTopic.ANIMAL,
			List.of(firstQuestion, secondQuestion),
			List.of(true, false),
			animalCompletedAt
		);
		saveCompletedSession(
			CHILD_ID,
			LearningTopic.ANIMAL,
			List.of(firstQuestion),
			List.of(true),
			secondAnimalCompletedAt
		);
		saveCompletedSession(
			CHILD_ID,
			LearningTopic.FOOD,
			List.of(firstQuestion),
			List.of(true),
			foodCompletedAt
		);
		saveInProgressSession(CHILD_ID);
		saveCanceledSession(CHILD_ID, foodCompletedAt.plusHours(1));
		saveCompletedSession(
			OTHER_CHILD_ID,
			LearningTopic.DAILY_LIFE,
			List.of(firstQuestion),
			List.of(true),
			foodCompletedAt
		);

		var overall = learningSessionRepository.findChildStatistics(
			List.of(CHILD_ID)
		);
		var topics = learningSessionRepository.findTopicStatistics(
			CHILD_ID,
			LearningSessionStatus.COMPLETED
		);
		var periods = learningSessionRepository.findLearningSessionPeriods(
			CHILD_ID,
			LearningSessionStatus.COMPLETED
		);

		assertThat(overall).singleElement().satisfies(statistics -> {
			assertThat(statistics.getChildId()).isEqualTo(CHILD_ID);
			assertThat(statistics.getTotalStudyCount()).isEqualTo(3);
			assertThat(statistics.getQuestionCount()).isEqualTo(4);
			assertThat(statistics.getCorrectCount()).isEqualTo(3);
		});
		assertThat(topics).satisfiesExactlyInAnyOrder(
			topic -> {
				assertThat(topic.getTopic()).isEqualTo(LearningTopic.ANIMAL);
				assertThat(topic.getQuestionCount()).isEqualTo(3);
				assertThat(topic.getCorrectCount()).isEqualTo(2);
			},
			topic -> {
				assertThat(topic.getTopic()).isEqualTo(LearningTopic.FOOD);
				assertThat(topic.getQuestionCount()).isEqualTo(1);
				assertThat(topic.getCorrectCount()).isEqualTo(1);
			}
		);
		assertThat(periods).hasSize(3);
		assertThat(periods.get(0).getCompletedAt()).isEqualTo(foodCompletedAt);
		assertThat(periods.get(1).getCompletedAt())
			.isEqualTo(secondAnimalCompletedAt);
		assertThat(periods.get(2).getCompletedAt()).isEqualTo(animalCompletedAt);
	}

	@Test
	void 여러_어린이의_학습_통계를_각각_집계한다() {
		LocalDateTime completedAt = LocalDateTime.of(2026, 8, 18, 11, 0);
		saveCompletedSession(
			CHILD_ID,
			LearningTopic.ANIMAL,
			List.of(firstQuestion, secondQuestion),
			List.of(true, false),
			completedAt
		);
		saveCompletedSession(
			OTHER_CHILD_ID,
			LearningTopic.FOOD,
			List.of(firstQuestion),
			List.of(true),
			completedAt
		);
		saveCompletedSession(
			OTHER_CHILD_ID,
			LearningTopic.DAILY_LIFE,
			List.of(secondQuestion),
			List.of(false),
			completedAt.plusMinutes(1)
		);

		var statistics = learningSessionRepository.findChildStatistics(
			List.of(CHILD_ID, OTHER_CHILD_ID)
		);

		assertThat(statistics).satisfiesExactlyInAnyOrder(
			child -> {
				assertThat(child.getChildId()).isEqualTo(CHILD_ID);
				assertThat(child.getTotalStudyCount()).isEqualTo(1);
				assertThat(child.getQuestionCount()).isEqualTo(2);
				assertThat(child.getCorrectCount()).isEqualTo(1);
			},
			child -> {
				assertThat(child.getChildId()).isEqualTo(OTHER_CHILD_ID);
				assertThat(child.getTotalStudyCount()).isEqualTo(2);
				assertThat(child.getQuestionCount()).isEqualTo(2);
				assertThat(child.getCorrectCount()).isEqualTo(1);
			}
		);
	}

	private LearningSession saveCompletedSession(
		Long childId,
		LearningTopic topic,
		List<Question> questions,
		List<Boolean> results,
		LocalDateTime completedAt
	) {
		LearningSession session = LearningSession.create(
			childId,
			topic,
			Difficulty.EASY,
			questions
		);
		results.forEach(session::completeCurrentQuestion);
		ReflectionTestUtils.setField(
			session,
			"startedAt",
			completedAt.minusMinutes(5)
		);
		ReflectionTestUtils.setField(session, "completedAt", completedAt);
		return learningSessionRepository.saveAndFlush(session);
	}

	private void saveInProgressSession(Long childId) {
		learningSessionRepository.saveAndFlush(LearningSession.create(
			childId,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(firstQuestion)
		));
	}

	private void saveCanceledSession(
		Long childId,
		LocalDateTime completedAt
	) {
		LearningSession session = LearningSession.create(
			childId,
			LearningTopic.DAILY_LIFE,
			Difficulty.EASY,
			List.of(firstQuestion)
		);
		session.cancel();
		ReflectionTestUtils.setField(session, "completedAt", completedAt);
		learningSessionRepository.saveAndFlush(session);
	}
}
