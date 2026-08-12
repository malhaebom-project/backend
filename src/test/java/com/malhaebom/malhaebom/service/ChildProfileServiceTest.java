package com.malhaebom.malhaebom.service;

import static com.malhaebom.malhaebom.support.ApiExceptionAssertions.assertApiException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.child.ChildLevel;
import com.malhaebom.malhaebom.domain.child.ChildProfile;
import com.malhaebom.malhaebom.domain.child.repository.ChildProfileRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.ChildStatisticsProjection;

@ExtendWith(MockitoExtension.class)
class ChildProfileServiceTest {

	private static final Long USER_ID = 1L;
	private static final Long CHILD_ID = 10L;

	@Mock
	private UserRepository userRepository;
	@Mock
	private ChildProfileRepository childProfileRepository;
	@Mock
	private LearningSessionRepository learningSessionRepository;
	@Mock
	private ChildStatisticsProjection statisticsProjection;

	@InjectMocks
	private ChildProfileService childProfileService;

	private User user;
	private ChildProfile profile;

	@BeforeEach
	void setUp() {
		user = User.create("Guardian", "guardian@example.com", "encoded-password");
		ReflectionTestUtils.setField(user, "id", USER_ID);
		profile = ChildProfile.create(user, "민수", 10, 3, ChildLevel.BEGINNER);
		ReflectionTestUtils.setField(profile, "id", CHILD_ID);
	}

	@Test
	void 프로필을_생성하며_별명_공백을_제거한다() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		when(childProfileRepository.existsByUserIdAndNicknameAndActiveTrue(USER_ID, "민수"))
			.thenReturn(false);
		when(childProfileRepository.save(any(ChildProfile.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		var result = childProfileService.create(
			USER_ID,
			"  민수  ",
			10,
			3,
			ChildLevel.BEGINNER
		);

		assertThat(result.profile().getNickname()).isEqualTo("민수");
		assertThat(result.statistics().totalStudyCount()).isZero();
	}

	@Test
	void 활성_프로필의_별명이_중복되면_생성할_수_없다() {
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		when(childProfileRepository.existsByUserIdAndNicknameAndActiveTrue(USER_ID, "민수"))
			.thenReturn(true);

		assertApiException(
			ErrorCode.CHILD_NICKNAME_ALREADY_EXISTS,
			() -> childProfileService.create(
				USER_ID,
				"민수",
				10,
				3,
				ChildLevel.BEGINNER
			)
		);
		verify(childProfileRepository, never()).save(any());
	}

	@Test
	void 다른_보호자의_프로필에는_접근할_수_없다() {
		when(childProfileRepository.findByIdAndActiveTrue(CHILD_ID))
			.thenReturn(Optional.of(profile));

		assertApiException(
			ErrorCode.CHILD_ACCESS_DENIED,
			() -> childProfileService.getOwnedActive(2L, CHILD_ID)
		);
	}

	@Test
	void 목록에_완료_학습_통계를_포함한다() {
		when(childProfileRepository.findAllByUserIdAndActiveTrueOrderByCreatedAtAsc(USER_ID))
			.thenReturn(List.of(profile));
		when(learningSessionRepository.findChildStatistics(List.of(CHILD_ID)))
			.thenReturn(List.of(statisticsProjection));
		when(statisticsProjection.getChildId()).thenReturn(CHILD_ID);
		when(statisticsProjection.getTotalStudyCount()).thenReturn(2L);
		when(statisticsProjection.getCorrectCount()).thenReturn(3L);
		when(statisticsProjection.getQuestionCount()).thenReturn(4L);

		var result = childProfileService.getAll(USER_ID).getFirst();

		assertThat(result.statistics().totalStudyCount()).isEqualTo(2);
		assertThat(result.statistics().totalCorrectRate()).isEqualTo(75.0);
	}

	@Test
	void 삭제하면_프로필을_비활성화한다() {
		when(childProfileRepository.findByIdAndActiveTrue(CHILD_ID))
			.thenReturn(Optional.of(profile));

		childProfileService.delete(USER_ID, CHILD_ID);

		assertThat(profile.isActive()).isFalse();
	}
}
