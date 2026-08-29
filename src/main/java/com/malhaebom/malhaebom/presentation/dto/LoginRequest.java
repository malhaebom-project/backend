package com.malhaebom.malhaebom.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public record LoginRequest(
	@NotBlank(message = "이메일은 필수입니다.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Schema(description = "가입한 보호자 계정의 이메일", example = "parent@example.com")
	String email,

	@NotBlank(message = "비밀번호는 필수입니다.")
	@Schema(description = "계정 비밀번호", example = "password1234")
	String password
) {}
