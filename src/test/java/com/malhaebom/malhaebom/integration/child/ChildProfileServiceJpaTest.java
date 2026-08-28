package com.malhaebom.malhaebom.integration.child;

import static com.malhaebom.malhaebom.support.LearningSessionTestActions.completeCurrentQuestion;
import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.child.ChildLevel;
import com.malhaebom.malhaebom.domain.child.ChildProfile;
import com.malhaebom.malhaebom.domain.child.repository.ChildProfileRepository;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.LearningSession;
import com.malhaebom.malhaebom.domain.learning.LearningTopic;
import com.malhaebom.malhaebom.domain.learning.Question;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.learning.repository.QuestionRepository;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.persistence.JpaAuditingConfiguration;
import com.malhaebom.malhaebom.service.ChildProfileService;

@DataJpaTest
@Import({ChildProfileService.class, JpaAuditingConfiguration.class})
class ChildProfileServiceJpaTest {
	@Autowired
	private ChildProfileService childProfileService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ChildProfileRepository childProfileRepository;
	@Autowired
	private QuestionRepository questionRepository;
	@Autowired
	private LearningSessionRepository learningSessionRepository;

	private Long userId;
	private Long childId;

	@BeforeEach
	void setUp() {
		User user = userRepository.saveAndFlush(
			User.create("Guardian", "guardian@example.com", "encoded-password")
		);
		ChildProfile profile = childProfileRepository.saveAndFlush(
			ChildProfile.create(user, "민수", 10, 3, ChildLevel.BEGINNER)
		);
		userId = user.getId();
		childId = profile.getId();
	}

	@Test
	void 프로필_생성_시_별명_공백을_제거하고_빈_통계를_반환한다() {
		var result = childProfileService.create(
			userId,
			"  영희  ",
			9,
			2,
			ChildLevel.ELEMENTARY
		);
		childProfileRepository.flush();

		assertThat(result.profile().getNickname()).isEqualTo("영희");
		assertThat(result.statistics().totalStudyCount()).isZero();
		assertThat(childProfileRepository.findById(result.profile().getId()))
			.isPresent();
	}

	@Test
	void 활성_프로필과_같은_별명은_새로_저장하지_않는다() {
		assertApiException(
			ErrorCode.CHILD_NICKNAME_ALREADY_EXISTS,
			() -> childProfileService.create(
				userId,
				"  민수  ",
				9,
				2,
				ChildLevel.ELEMENTARY
			)
		);

		assertThat(childProfileRepository.count()).isEqualTo(1);
	}

	@Test
	void 다른_보호자의_프로필에는_접근할_수_없다() {
		assertApiException(
			ErrorCode.CHILD_ACCESS_DENIED,
			() -> childProfileService.getOwnedActive(userId + 1, childId)
		);
	}

	@Test
	void 완료된_학습의_횟수와_정답률을_집계한다() {
		Question question = questionRepository.saveAndFlush(Question.create(
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			QuestionType.SHORT_ANSWER,
			"What is this?",
			"이것은 무엇인가요?",
			null,
			"",
			"It is a cat.",
			Set.of("It is a cat."),
			null,
			null
		));
		LearningSession session = LearningSession.create(
			childId,
			LearningTopic.ANIMAL,
			Difficulty.EASY,
			List.of(question)
		);
		completeCurrentQuestion(session, true);
		learningSessionRepository.saveAndFlush(session);

		var result = childProfileService.get(userId, childId);

		assertThat(result.statistics().totalStudyCount()).isEqualTo(1);
		assertThat(result.statistics().totalCorrectRate()).isEqualTo(100.0);
	}

	@Test
	void 삭제한_프로필은_목록에서_제외한다() {
		childProfileService.delete(userId, childId);
		childProfileRepository.flush();

		assertThat(childProfileService.getAll(userId)).isEmpty();
		assertThat(childProfileRepository.findById(childId)).isPresent();
	}
}
