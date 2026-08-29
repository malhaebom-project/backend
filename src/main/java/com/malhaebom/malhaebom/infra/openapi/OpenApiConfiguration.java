package com.malhaebom.malhaebom.infra.openapi;

import com.malhaebom.malhaebom.presentation.dto.ApiErrorResponse;
import com.malhaebom.malhaebom.presentation.cookie.RefreshCookieProvider;
import com.malhaebom.malhaebom.presentation.cookie.RefreshTokenCookieProperties;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
	public OperationCustomizer apiDocumentationOperationCustomizer(
		RefreshTokenCookieProperties refreshCookieProperties
	) {
		return (operation, handlerMethod) -> {
			applyControllerMetadata(operation, handlerMethod.getBeanType());
			boolean authenticated = handlerMethod.hasMethodAnnotation(AuthenticatedErrorResponses.class)
				|| handlerMethod.getBeanType().isAnnotationPresent(AuthenticatedErrorResponses.class);
			boolean validated = handlerMethod.hasMethodAnnotation(ValidationErrorResponses.class)
				|| handlerMethod.getBeanType().isAnnotationPresent(ValidationErrorResponses.class);

			ApiResponses responses = operation.getResponses();
			if (responses == null) {
				responses = new ApiResponses();
				operation.setResponses(responses);
			}
			SuccessfulResponse successfulResponse = handlerMethod
				.getMethodAnnotation(SuccessfulResponse.class);
			if (successfulResponse != null) {
				applySuccessfulResponse(responses, successfulResponse);
			}
			RefreshCookieResponse refreshCookieResponse = handlerMethod
				.getMethodAnnotation(RefreshCookieResponse.class);
			if (refreshCookieResponse != null && successfulResponse != null) {
				applyRefreshCookieResponse(
					responses,
					successfulResponse,
					refreshCookieResponse,
					refreshCookieProperties
				);
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
					Map.of(
						"UNAUTHORIZED", errorExample(
							"인증이 필요합니다.",
							"UNAUTHORIZED"
						),
						"INVALID_ACCESS_TOKEN", errorExample(
							"유효하지 않은 인증 정보입니다.",
							"UNAUTHORIZED"
						),
						"EXPIRED_ACCESS_TOKEN", errorExample(
							"만료된 액세스 토큰입니다.",
							"UNAUTHORIZED"
						)
					)
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
			if (handlerMethod.hasMethodAnnotation(SpeechProcessingErrorResponses.class)) {
				markSpeechAudioAsRequired(operation);
				addSpeechProcessingResponses(responses);
			}
			if (handlerMethod.hasMethodAnnotation(AnswerSubmissionErrorResponses.class)) {
				addAnswerSubmissionResponses(responses);
			}
			addDomainErrorResponses(responses, handlerMethod.getBeanType()
				.getAnnotationsByType(DomainErrorResponses.class));
			addDomainErrorResponses(responses, handlerMethod.getMethod()
				.getAnnotationsByType(DomainErrorResponses.class));
			return operation;
		};
	}

	private void applyControllerMetadata(
		io.swagger.v3.oas.models.Operation operation,
		Class<?> controllerType
	) {
		String controllerName = controllerType.getSimpleName();
		String controllerDescription = "**Controller:** `" + controllerName + "`";
		String description = operation.getDescription();
		operation.setDescription(
			description == null || description.isBlank()
				? controllerDescription
				: description + "\n\n" + controllerDescription
		);
		operation.addExtension("x-controller", controllerName);
	}

	private void applyRefreshCookieResponse(
		ApiResponses responses,
		SuccessfulResponse successfulResponse,
		RefreshCookieResponse refreshCookieResponse,
		RefreshTokenCookieProperties properties
	) {
		ApiResponse response = responses.get(Integer.toString(successfulResponse.status()));
		if (response == null) {
			return;
		}

		boolean expires = refreshCookieResponse.value() == RefreshCookieResponse.Action.EXPIRE;
		String attributes = cookieAttributes(properties, expires);
		String description = expires
			? "브라우저의 Refresh Token 쿠키를 삭제하도록 만료시킵니다. " + attributes
			: "새 Refresh Token을 HttpOnly 쿠키로 발급합니다. 로그인과 재발급 때마다 값이 갱신됩니다. "
				+ attributes;
		String value = expires ? "" : "<refresh-token>";
		String example = RefreshCookieProvider.REFRESH_TOKEN_KEY + "=" + value + "; "
			+ cookieDirectives(properties, expires);

		response.addHeaderObject(
			"Set-Cookie",
			new io.swagger.v3.oas.models.headers.Header()
				.description(description)
				.schema(new StringSchema().example(example))
		);
	}

	private String cookieAttributes(RefreshTokenCookieProperties properties, boolean expires) {
		return "쿠키 이름은 `" + RefreshCookieProvider.REFRESH_TOKEN_KEY + "`, "
			+ "Path=" + properties.getPath()
			+ domainDescription(properties)
			+ ", SameSite=" + properties.getSameSite()
			+ ", Secure=" + properties.isSecure()
			+ ", HttpOnly=" + properties.isHttpOnly()
			+ ", Max-Age=" + (expires ? 0 : properties.getTtl().toSeconds()) + "초입니다.";
	}

	private String cookieDirectives(RefreshTokenCookieProperties properties, boolean expires) {
		String directives = "Path=" + properties.getPath()
			+ domainDirective(properties)
			+ "; Max-Age=" + (expires ? 0 : properties.getTtl().toSeconds())
			+ "; SameSite=" + properties.getSameSite();
		if (properties.isSecure()) {
			directives += "; Secure";
		}
		if (properties.isHttpOnly()) {
			directives += "; HttpOnly";
		}
		return directives;
	}

	private String domainDescription(RefreshTokenCookieProperties properties) {
		return properties.getDomain() == null || properties.getDomain().isBlank()
			? ", Domain 미지정(host-only)"
			: ", Domain=" + properties.getDomain();
	}

	private String domainDirective(RefreshTokenCookieProperties properties) {
		return properties.getDomain() == null || properties.getDomain().isBlank()
			? ""
			: "; Domain=" + properties.getDomain();
	}

	private void applySuccessfulResponse(
		ApiResponses responses,
		SuccessfulResponse successfulResponse
	) {
		String responseCode = Integer.toString(successfulResponse.status());
		ApiResponse response = responses.get(responseCode);
		if (response == null && !"200".equals(responseCode)) {
			response = responses.remove("200");
		}
		if (response == null) {
			response = new ApiResponse();
		}
		response.setDescription(successfulResponse.description());
		if (successfulResponse.status() == 204) {
			response.setContent(null);
		}
		responses.addApiResponse(responseCode, response);
	}

	private void addDomainErrorResponses(
		ApiResponses responses,
		DomainErrorResponses[] annotations
	) {
		Map<Integer, List<OpenApiErrorExample>> errorsByStatus = Arrays.stream(annotations)
			.flatMap(annotation -> java.util.stream.Stream.concat(
				Arrays.stream(annotation.value())
					.map(errorCode -> new OpenApiErrorExample(
						errorCode,
						errorCode.name(),
						errorCode.getMessage()
					)),
				Arrays.stream(annotation.examples())
					.map(example -> new OpenApiErrorExample(
						example.code(),
						example.name().isBlank() ? example.code().name() : example.name(),
						example.message()
					))
			))
			.distinct()
			.collect(Collectors.groupingBy(
				example -> example.errorCode().getHttpStatus().value(),
				LinkedHashMap::new,
				Collectors.toList()
			));

		errorsByStatus.forEach((status, errorCodes) ->
			mergeDomainErrorResponse(responses, status.toString(), errorCodes));
	}

	private void mergeDomainErrorResponse(
		ApiResponses responses,
		String status,
		List<OpenApiErrorExample> examples
	) {
		ApiResponse response = responses.get(status);
		if (response == null) {
			response = errorResponse(
				examples.stream()
					.map(OpenApiErrorExample::message)
					.distinct()
					.collect(Collectors.joining(" / ")),
				new LinkedHashMap<>()
			);
			responses.addApiResponse(status, response);
		}

		MediaType mediaType = response.getContent().get("application/json");
		if (mediaType.getExamples() == null) {
			mediaType.setExamples(new LinkedHashMap<>());
		}
		examples.forEach(example -> mediaType.addExamples(
			example.name(),
			errorExample(example.message(), example.errorCode().name())
		));
	}

	private record OpenApiErrorExample(
		ErrorCode errorCode,
		String name,
		String message
	) {}

	private void markSpeechAudioAsRequired(io.swagger.v3.oas.models.Operation operation) {
		if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
			return;
		}
		operation.getRequestBody().setRequired(true);
		MediaType multipart = operation.getRequestBody().getContent().get("multipart/form-data");
		if (multipart != null && multipart.getSchema() != null) {
			multipart.getSchema().addRequiredItem("audio");
		}
	}

	private void addSpeechProcessingResponses(ApiResponses responses) {
		responses.addApiResponse("404", singleErrorResponse(
			"학습 세션을 찾을 수 없음",
			"LEARNING_SESSION_NOT_FOUND",
			"학습 세션을 찾을 수 없습니다."
		));
		responses.addApiResponse("409", singleErrorResponse(
			"같은 음성 답변을 처리하고 있음",
			"SPEECH_PROCESSING",
			"음성 답변을 처리하고 있습니다."
		));
		responses.addApiResponse("422", singleErrorResponse(
			"음성에서 답변을 인식하지 못함",
			"SPEECH_NOT_RECOGNIZED",
			"음성을 인식하지 못했습니다."
		));
		responses.addApiResponse("429", singleErrorResponse(
			"외부 음성 인식 요청 한도 초과",
			"AI_REQUEST_LIMIT_EXCEEDED",
			"음성 인식 요청이 많습니다. 잠시 후 다시 시도해 주세요."
		));
		responses.addApiResponse("500", singleErrorResponse(
			"음성 변환 처리 실패",
			"STT_PROCESSING_FAILED",
			"음성 변환 처리에 실패했습니다."
		));
		responses.addApiResponse("503", singleErrorResponse(
			"음성 변환 처리 용량 초과",
			"STT_PROCESSING_OVERLOADED",
			"음성 변환 요청이 많습니다. 잠시 후 다시 시도해 주세요."
		));
		responses.addApiResponse("504", singleErrorResponse(
			"음성 변환 처리시간 초과",
			"STT_PROCESSING_TIMEOUT",
			"음성 변환 처리 시간이 초과되었습니다."
		));
	}

	private void addAnswerSubmissionResponses(ApiResponses responses) {
		responses.addApiResponse("404", errorResponse(
			"학습 세션 또는 음성 답변을 찾을 수 없음",
			Map.of(
				"LEARNING_SESSION_NOT_FOUND", errorExample(
					"학습 세션을 찾을 수 없습니다.",
					"LEARNING_SESSION_NOT_FOUND"
				),
				"SPEECH_ANSWER_NOT_FOUND", errorExample(
					"음성 답변을 찾을 수 없습니다.",
					"SPEECH_ANSWER_NOT_FOUND"
				)
			)
		));
		responses.addApiResponse("409", errorResponse(
			"답변 제출이 처리 중이거나 충돌함",
			Map.of(
				"ANSWER_SUBMISSION_PROCESSING", errorExample(
					"답변 제출을 처리하고 있습니다.",
					"ANSWER_SUBMISSION_PROCESSING"
				),
				"ANSWER_SUBMISSION_CONFLICT", errorExample(
					"처리 중이거나 재시도할 답변 제출이 이미 있습니다.",
					"ANSWER_SUBMISSION_CONFLICT"
				)
			)
		));
		responses.addApiResponse("502", singleErrorResponse(
			"외부 답변 채점 실패",
			"ANSWER_ASSESSMENT_FAILED",
			"답변 채점에 실패했습니다. 잠시 후 다시 시도해 주세요."
		));
		responses.addApiResponse("503", singleErrorResponse(
			"답변 채점 처리 용량 초과",
			"ANSWER_ASSESSMENT_OVERLOADED",
			"답변 채점 요청이 많습니다. 잠시 후 다시 시도해 주세요."
		));
		responses.addApiResponse("504", singleErrorResponse(
			"답변 제출 처리시간 초과",
			"ANSWER_SUBMISSION_TIMEOUT",
			"답변 제출 처리 시간이 초과되었습니다."
		));
	}

	private ApiResponse singleErrorResponse(
		String description,
		String errorCode,
		String message
	) {
		return errorResponse(
			description,
			Map.of(errorCode, errorExample(message, errorCode))
		);
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
