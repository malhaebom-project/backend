package com.malhaebom.malhaebom.infra.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
	info = @Info(
		title = "말해봄 API",
		description = "말하기 학습 서비스 '말해봄'의 REST API 문서입니다. "
			+ "인증이 필요한 API는 Authorize에서 액세스 토큰을 입력해 사용할 수 있습니다.",
		version = "v1"
	)
)
@SecurityScheme(
	name = "bearerAuth",
	description = "로그인 API에서 발급받은 JWT 액세스 토큰",
	type = SecuritySchemeType.HTTP,
	scheme = "bearer",
	bearerFormat = "JWT"
)
public class OpenApiConfiguration {}
