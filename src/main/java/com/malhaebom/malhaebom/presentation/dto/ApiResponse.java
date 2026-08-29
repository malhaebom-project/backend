package com.malhaebom.malhaebom.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "API 공통 응답")
public record ApiResponse<T>(
	@Schema(description = "요청 성공 여부", example = "true")
	boolean success,
	@Schema(description = "요청별 응답 데이터. 데이터가 없는 성공 응답은 null", nullable = true)
	T data,
	@Schema(description = "사용자에게 표시할 안내 메시지. 메시지가 없으면 null", nullable = true)
	String message,
	@JsonInclude(JsonInclude.Include.NON_NULL)
	@Schema(description = "실패 시 클라이언트가 분기 처리할 오류 코드. 성공 응답에서는 생략", nullable = true)
	String errorCode
) {
	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null, null);
	}

	public static <T> ApiResponse<T> success(T data, String message) {
		return new ApiResponse<>(true, data, message, null);
	}

	public static <T> ApiResponse<T> error(String message, String errorCode) {
		return new ApiResponse<>(false, null, message, errorCode);
	}
}
