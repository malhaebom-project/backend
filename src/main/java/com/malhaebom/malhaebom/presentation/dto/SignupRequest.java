package com.malhaebom.malhaebom.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "보호자 회원가입 요청")
public record SignupRequest(
	@NotBlank(message = "이름은 필수입니다.")
	@Schema(description = "보호자 이름", example = "홍길동")
	String name,

	@NotBlank(message = "이메일은 필수입니다.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Schema(description = "로그인에 사용할 이메일", example = "parent@example.com")
	String email,

	@NotBlank(message = "비밀번호는 필수입니다.")
	@Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
	@Schema(description = "8자 이상의 비밀번호", example = "password1234", minLength = 8)
	String password
) {}
