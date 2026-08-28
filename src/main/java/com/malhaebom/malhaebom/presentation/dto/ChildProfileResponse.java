package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.child.ChildLevel;
import com.malhaebom.malhaebom.domain.child.ChildProfile;
import com.malhaebom.malhaebom.service.dto.ChildStatistics;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자녀 프로필과 누적 학습 통계")
public record ChildProfileResponse(
	@Schema(description = "자녀 프로필 ID", example = "1")
	Long childId,
	@Schema(description = "자녀 닉네임", example = "봄이")
	String nickname,
	@Schema(description = "한국식 나이", example = "8")
	int age,
	@Schema(description = "초등학교 학년", example = "1")
	int grade,
	@Schema(description = "현재 학습 레벨", example = "BEGINNER")
	ChildLevel level,
	@Schema(description = "완료한 전체 학습 세션 수", example = "12")
	long totalStudyCount,
	@Schema(description = "누적 정답률", example = "82.5")
	double totalCorrectRate
) {
	public static ChildProfileResponse from(ChildProfile profile, ChildStatistics statistics) {
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
