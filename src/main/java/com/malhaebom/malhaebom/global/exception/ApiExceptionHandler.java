package com.malhaebom.malhaebom.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.malhaebom.malhaebom.presentation.dto.ApiResponse;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(LearningSessionNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(
		LearningSessionNotFoundException exception
	) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiResponse.error(exception.getMessage()));
	}

	@ExceptionHandler({
		IllegalArgumentException.class,
		IllegalStateException.class
	})
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(
		RuntimeException exception
	) {
		return ResponseEntity.badRequest()
			.body(ApiResponse.error(exception.getMessage()));
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
			.body(ApiResponse.error(message));
	}
}
