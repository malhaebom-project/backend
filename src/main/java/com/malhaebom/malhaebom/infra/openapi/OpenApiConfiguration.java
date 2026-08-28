package com.malhaebom.malhaebom.infra.openapi;

import com.malhaebom.malhaebom.presentation.dto.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

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
public class OpenApiConfiguration {
	private static final String ERROR_SCHEMA_REF = "#/components/schemas/ApiErrorResponse";

	@Bean
	public OpenApiCustomizer apiErrorResponseSchemaCustomizer() {
		return openApi -> ModelConverters.getInstance()
			.read(ApiErrorResponse.class)
			.forEach(openApi.getComponents()::addSchemas);
	}

	@Bean
	public OperationCustomizer commonErrorResponseCustomizer() {
		return (operation, handlerMethod) -> {
			boolean authenticated = handlerMethod.hasMethodAnnotation(AuthenticatedErrorResponses.class)
				|| handlerMethod.getBeanType().isAnnotationPresent(AuthenticatedErrorResponses.class);
			boolean validated = handlerMethod.hasMethodAnnotation(ValidationErrorResponses.class)
				|| handlerMethod.getBeanType().isAnnotationPresent(ValidationErrorResponses.class);

			ApiResponses responses = operation.getResponses();
			if (responses == null) {
				responses = new ApiResponses();
				operation.setResponses(responses);
			}
			if (validated) {
				responses.addApiResponse("400", errorResponse(
					"요청 값이 올바르지 않음",
					Map.of("INVALID_REQUEST", errorExample(
						"요청 값이 올바르지 않습니다.",
						"INVALID_REQUEST"
					))
				));
			}
			if (authenticated) {
				responses.addApiResponse("401", errorResponse(
					"인증 정보가 없거나 유효하지 않음",
					Map.of("UNAUTHORIZED", errorExample(
						"인증이 필요합니다.",
						"UNAUTHORIZED"
					))
				));
				responses.addApiResponse("403", errorResponse(
					"리소스에 접근할 권한이 없음",
					Map.of(
						"FORBIDDEN", errorExample(
							"접근 권한이 없습니다.",
							"FORBIDDEN"
						),
						"CHILD_ACCESS_DENIED", errorExample(
							"어린이 프로필에 접근할 권한이 없습니다.",
							"CHILD_ACCESS_DENIED"
						)
					)
				));
			}
			return operation;
		};
	}

	private ApiResponse errorResponse(String description, Map<String, Example> examples) {
		MediaType mediaType = new MediaType()
			.schema(new Schema<>().$ref(ERROR_SCHEMA_REF));
		examples.forEach(mediaType::addExamples);
		return new ApiResponse()
			.description(description)
			.content(new Content().addMediaType("application/json", mediaType));
	}

	private Example errorExample(String message, String errorCode) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("success", false);
		value.put("data", null);
		value.put("message", message);
		value.put("errorCode", errorCode);
		return new Example().value(value);
	}
}
