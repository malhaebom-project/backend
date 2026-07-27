package com.malhaebom.malhaebom.presentation.dto;

public record ApiResponse<T>(
	boolean success,
	T data,
	String message
) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}
}
