package com.malhaebom.malhaebom.global.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.malhaebom.malhaebom.presentation.dto.ApiResponse;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiResponse<Void>> handleApiException(
		ApiException exception
	) {
		ErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(errorCode.getHttpStatus())
			.body(ApiResponse.error(
				exception.getMessage(),
				errorCode.name()
			));
	}

	@ExceptionHandler({
		IllegalArgumentException.class,
		IllegalStateException.class
	})
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(
		RuntimeException exception
	) {
		return ResponseEntity.badRequest()
			.body(ApiResponse.error(
				exception.getMessage(),
				ErrorCode.INVALID_REQUEST.name()
			));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(
		MethodArgumentNotValidException exception
	) {
		String message = exception.getBindingResult()
			.getFieldErrors()
			.getFirst()
			.getDefaultMessage();
		return ResponseEntity.badRequest()
			.body(ApiResponse.error(
				message,
				ErrorCode.INVALID_REQUEST.name()
			));
	}
}
