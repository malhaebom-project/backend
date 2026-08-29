package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.child.ChildLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "어린이 프로필 생성 요청")
public record CreateChildProfileRequest(
	@NotBlank(message = "별명은 필수입니다.")
	@Size(max = 30, message = "별명은 30자 이하여야 합니다.")
	@Schema(description = "어린이 별명", example = "영어왕민수", maxLength = 30)
	String nickname,

	@Positive(message = "나이는 1 이상이어야 합니다.")
	@Schema(description = "어린이 나이", example = "10", minimum = "1")
	int age,

	@Min(value = 1, message = "학년은 1 이상이어야 합니다.")
	@Max(value = 6, message = "학년은 6 이하여야 합니다.")
	@Schema(description = "초등학교 학년", example = "3", minimum = "1", maximum = "6")
	int grade,

	@NotNull(message = "영어 단계는 필수입니다.")
	@Schema(description = "현재 영어 학습 단계", example = "BEGINNER")
	ChildLevel level
) {}
