package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.child.ChildLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "어린이 프로필 수정 요청. 변경할 필드를 하나 이상 입력합니다.")
public record UpdateChildProfileRequest(
	@Pattern(regexp = ".*\\S.*", message = "별명은 비어 있을 수 없습니다.")
	@Size(max = 30, message = "별명은 30자 이하여야 합니다.")
	@Schema(description = "변경할 어린이 별명", example = "새봄이", maxLength = 30)
	String nickname,

	@Positive(message = "나이는 1 이상이어야 합니다.")
	@Schema(description = "변경할 만 나이", example = "10", minimum = "1")
	Integer age,

	@Min(value = 1, message = "학년은 1 이상이어야 합니다.")
	@Max(value = 6, message = "학년은 6 이하여야 합니다.")
	@Schema(description = "변경할 초등학교 학년", example = "4", minimum = "1", maximum = "6")
	Integer grade,

	@Schema(description = "변경할 영어 학습 단계", example = "ELEMENTARY")
	ChildLevel level
) {
	@AssertTrue(message = "수정할 프로필 정보를 하나 이상 입력해야 합니다.")
	@Schema(hidden = true)
	public boolean isAnyFieldPresent() {
		return nickname != null || age != null || grade != null || level != null;
	}
}
