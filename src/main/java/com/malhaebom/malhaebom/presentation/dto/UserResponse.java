package com.malhaebom.malhaebom.presentation.dto;

import com.malhaebom.malhaebom.domain.AccountRole;
import com.malhaebom.malhaebom.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보호자 계정 정보")
public record UserResponse(
	@Schema(description = "보호자 ID", example = "1") Long guardianId,
	@Schema(description = "로그인 이메일", example = "parent@example.com") String email,
	@Schema(description = "보호자 이름", example = "홍길동") String name,
	@Schema(description = "계정 권한", example = "GUARDIAN") AccountRole role
) {
	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
	}
}
