package com.malhaebom.malhaebom.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증 성공 후 발급된 액세스 토큰")
public record AccessTokenResponse(
	@Schema(description = "Authorization Bearer 헤더에 사용할 JWT 액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature")
	String accessToken
) {}
