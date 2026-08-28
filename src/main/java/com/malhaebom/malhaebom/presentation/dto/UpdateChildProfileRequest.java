package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.child.ChildLevel;
import jakarta.validation.constraints.*;

public record UpdateChildProfileRequest(
	@Pattern(regexp = ".*\\S.*", message = "별명은 비어 있을 수 없습니다.")
	@Size(max = 30, message = "별명은 30자 이하여야 합니다.")
	String nickname,

	@Positive(message = "나이는 1 이상이어야 합니다.")
	Integer age,

	@Min(value = 1, message = "학년은 1 이상이어야 합니다.")
	@Max(value = 6, message = "학년은 6 이하여야 합니다.")
	Integer grade,

	ChildLevel level
) {
	@AssertTrue(message = "수정할 프로필 정보를 하나 이상 입력해야 합니다.")
	public boolean isAnyFieldPresent() {
		return nickname != null || age != null || grade != null || level != null;
	}
}
