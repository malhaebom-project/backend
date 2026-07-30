package com.malhaebom.malhaebom.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiResponse<T>(
	boolean success,
	T data,
	String message,
	@JsonInclude(JsonInclude.Include.NON_NULL)
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
