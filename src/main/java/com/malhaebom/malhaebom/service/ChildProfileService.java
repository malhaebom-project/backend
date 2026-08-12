package com.malhaebom.malhaebom.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malhaebom.malhaebom.domain.User;
import com.malhaebom.malhaebom.domain.child.ChildLevel;
import com.malhaebom.malhaebom.domain.child.ChildProfile;
import com.malhaebom.malhaebom.domain.child.repository.ChildProfileRepository;
import com.malhaebom.malhaebom.domain.learning.repository.LearningSessionRepository;
import com.malhaebom.malhaebom.domain.repository.UserRepository;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.ChildProfileResult;
import com.malhaebom.malhaebom.service.dto.ChildStatistics;
import com.malhaebom.malhaebom.service.dto.ChildStatisticsProjection;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChildProfileService {

	private final UserRepository userRepository;
	private final ChildProfileRepository childProfileRepository;
	private final LearningSessionRepository learningSessionRepository;

	@Transactional
	public ChildProfileResult create(
		Long userId,
		String nickname,
		int age,
		int grade,
		ChildLevel level
	) {
		User user = getUser(userId);
		String normalizedNickname = nickname.trim();
		validateNicknameAvailable(userId, normalizedNickname, null);
		ChildProfile profile = childProfileRepository.save(
			ChildProfile.create(user, normalizedNickname, age, grade, level)
		);
		return new ChildProfileResult(profile, ChildStatistics.empty());
	}

	@Transactional(readOnly = true)
	public List<ChildProfileResult> getAll(Long userId) {
		List<ChildProfile> profiles =
			childProfileRepository.findAllByUserIdAndActiveTrueOrderByCreatedAtAsc(userId);
		Map<Long, ChildStatistics> statistics = getStatistics(profiles);
		return profiles.stream()
			.map(profile -> new ChildProfileResult(
				profile,
				statistics.getOrDefault(profile.getId(), ChildStatistics.empty())
			))
			.toList();
	}

	@Transactional(readOnly = true)
	public ChildProfileResult get(Long userId, Long childId) {
		ChildProfile profile = getOwnedActive(userId, childId);
		return new ChildProfileResult(profile, getStatistics(List.of(profile))
			.getOrDefault(childId, ChildStatistics.empty()));
	}

	@Transactional
	public ChildProfileResult update(
		Long userId,
		Long childId,
		String nickname,
		Integer age,
		Integer grade,
		ChildLevel level
	) {
		ChildProfile profile = getOwnedActive(userId, childId);
		String normalizedNickname = nickname == null ? null : nickname.trim();
		if (normalizedNickname != null) {
			validateNicknameAvailable(userId, normalizedNickname, childId);
		}
		profile.update(normalizedNickname, age, grade, level);
		ChildStatistics statistics = getStatistics(List.of(profile))
			.getOrDefault(childId, ChildStatistics.empty());
		return new ChildProfileResult(profile, statistics);
	}

	@Transactional
	public void delete(Long userId, Long childId) {
		getOwnedActive(userId, childId).deactivate();
	}

	@Transactional(readOnly = true)
	public ChildProfile getOwnedActive(Long userId, Long childId) {
		ChildProfile profile = childProfileRepository.findByIdAndActiveTrue(childId)
			.orElseThrow(() -> new ApiException(ErrorCode.CHILD_PROFILE_NOT_FOUND));
		if (!profile.isOwnedBy(userId)) {
			throw new ApiException(ErrorCode.CHILD_ACCESS_DENIED);
		}
		return profile;
	}

	private User getUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
	}

	private void validateNicknameAvailable(
		Long userId,
		String nickname,
		Long excludedChildId
	) {
		boolean exists = excludedChildId == null
			? childProfileRepository.existsByUserIdAndNicknameAndActiveTrue(userId, nickname)
			: childProfileRepository.existsByUserIdAndNicknameAndActiveTrueAndIdNot(
				userId,
				nickname,
				excludedChildId
			);
		if (exists) {
			throw new ApiException(ErrorCode.CHILD_NICKNAME_ALREADY_EXISTS);
		}
	}

	private Map<Long, ChildStatistics> getStatistics(List<ChildProfile> profiles) {
		if (profiles.isEmpty()) {
			return Map.of();
		}
		List<Long> childIds = profiles.stream().map(ChildProfile::getId).toList();
		return learningSessionRepository.findChildStatistics(childIds).stream()
			.collect(Collectors.toMap(
				ChildStatisticsProjection::getChildId,
				this::toStatistics,
				(first, ignored) -> first
			));
	}

	private ChildStatistics toStatistics(ChildStatisticsProjection projection) {
		return new ChildStatistics(
			projection.getTotalStudyCount(),
			projection.getCorrectCount(),
			projection.getQuestionCount()
		);
	}
}
