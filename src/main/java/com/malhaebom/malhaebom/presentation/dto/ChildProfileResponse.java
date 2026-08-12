package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.child.ChildLevel;
import com.malhaebom.malhaebom.domain.child.ChildProfile;
import com.malhaebom.malhaebom.service.dto.ChildStatistics;

public record ChildProfileResponse(
	Long childId,
	String nickname,
	int age,
	int grade,
	ChildLevel level,
	long totalStudyCount,
	double totalCorrectRate
) {

	public static ChildProfileResponse from(
		ChildProfile profile,
		ChildStatistics statistics
	) {
		return new ChildProfileResponse(
			profile.getId(),
			profile.getNickname(),
			profile.getAge(),
			profile.getGrade(),
			profile.getLevel(),
			statistics.totalStudyCount(),
			statistics.totalCorrectRate()
		);
	}
}
