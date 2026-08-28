package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.child.ChildLevel;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateChildProfileRequest(
	@NotBlank(message = "별명은 필수입니다.")
	@Size(max = 30, message = "별명은 30자 이하여야 합니다.")
	String nickname,

	@Positive(message = "나이는 1 이상이어야 합니다.")
	int age,

	@Min(value = 1, message = "학년은 1 이상이어야 합니다.")
	@Max(value = 6, message = "학년은 6 이하여야 합니다.")
	int grade,

	@NotNull(message = "영어 단계는 필수입니다.")
	ChildLevel level
) {}
