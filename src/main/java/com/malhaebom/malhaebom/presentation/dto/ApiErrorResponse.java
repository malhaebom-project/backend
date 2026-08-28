package com.malhaebom.malhaebom.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "API 오류 응답")
public record ApiErrorResponse(
	@Schema(description = "요청 성공 여부", example = "false", allowableValues = "false")
	boolean success,

	@Schema(description = "오류 응답에서는 항상 null", nullable = true)
	Object data,

	@Schema(description = "사용자에게 표시할 오류 메시지", example = "요청 값이 올바르지 않습니다.")
	String message,

	@Schema(description = "클라이언트가 분기 처리할 오류 코드", example = "INVALID_REQUEST")
	String errorCode
) {}
