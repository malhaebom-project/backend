package com.malhaebom.malhaebom.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.malhaebom.malhaebom.presentation.dto.ApiResponse;

class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@ParameterizedTest
	@MethodSource("apiExceptions")
	void API_예외를_HTTP_상태와_오류_코드로_변환한다(
		ApiException exception,
		HttpStatus expectedStatus,
		ErrorCode expectedErrorCode
	) {
		ResponseEntity<ApiResponse<Void>> response =
			handler.handleApiException(exception);

		assertErrorResponse(
			response,
			expectedStatus,
			exception.getMessage(),
			expectedErrorCode
		);
	}

	@ParameterizedTest
	@MethodSource("invalidRequestExceptions")
	void 잘못된_요청_예외를_INVALID_REQUEST로_변환한다(
		RuntimeException exception
	) {
		ResponseEntity<ApiResponse<Void>> response =
			handler.handleBadRequest(exception);

		assertErrorResponse(
			response,
			HttpStatus.BAD_REQUEST,
			exception.getMessage(),
			ErrorCode.INVALID_REQUEST
		);
	}

	@Test
	void Validation_예외를_INVALID_REQUEST로_변환한다() {
		BeanPropertyBindingResult bindingResult =
			new BeanPropertyBindingResult(new Object(), "request");
		bindingResult.addError(
			new FieldError("request", "field", "필수 값입니다.")
		);
		MethodArgumentNotValidException exception =
			new MethodArgumentNotValidException(null, bindingResult);

		ResponseEntity<ApiResponse<Void>> response =
			handler.handleValidation(exception);

		assertErrorResponse(
			response,
			HttpStatus.BAD_REQUEST,
			"필수 값입니다.",
			ErrorCode.INVALID_REQUEST
		);
	}

	private void assertErrorResponse(
		ResponseEntity<ApiResponse<Void>> response,
		HttpStatus expectedStatus,
		String expectedMessage,
		ErrorCode expectedErrorCode
	) {
		ApiResponse<Void> body = response.getBody();

		assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
		assertThat(body).isNotNull();
		assertThat(body.success()).isFalse();
		assertThat(body.data()).isNull();
		assertThat(body.message()).isEqualTo(expectedMessage);
		assertThat(body.errorCode()).isEqualTo(expectedErrorCode.name());
	}

	private static Stream<Arguments> apiExceptions() {
		return Stream.of(
			Arguments.of(
				new UnauthorizedException("인증에 실패했습니다."),
				HttpStatus.UNAUTHORIZED,
				ErrorCode.UNAUTHORIZED
			),
			Arguments.of(
				new NotFoundException("요청한 리소스를 찾을 수 없습니다."),
				HttpStatus.NOT_FOUND,
				ErrorCode.NOT_FOUND
			),
			Arguments.of(
				new LearningSessionNotFoundException(),
				HttpStatus.NOT_FOUND,
				ErrorCode.LEARNING_SESSION_NOT_FOUND
			),
			Arguments.of(
				new InvalidAudioFileException(),
				HttpStatus.BAD_REQUEST,
				ErrorCode.INVALID_AUDIO_FILE
			),
			Arguments.of(
				new CurrentQuestionMismatchException(),
				HttpStatus.BAD_REQUEST,
				ErrorCode.CURRENT_QUESTION_MISMATCH
			),
			Arguments.of(
				new SpeechAnswerNotFoundException(),
				HttpStatus.NOT_FOUND,
				ErrorCode.SPEECH_ANSWER_NOT_FOUND
			),
			Arguments.of(
				new SpeechProcessingException(),
				HttpStatus.CONFLICT,
				ErrorCode.SPEECH_PROCESSING
			),
			Arguments.of(
				new SpeechNotRecognizedException(),
				HttpStatus.UNPROCESSABLE_CONTENT,
				ErrorCode.SPEECH_NOT_RECOGNIZED
			),
			Arguments.of(
				new AiRequestLimitExceededException(),
				HttpStatus.TOO_MANY_REQUESTS,
				ErrorCode.AI_REQUEST_LIMIT_EXCEEDED
			),
			Arguments.of(
				new SpeechProcessingFailedException(),
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCode.STT_PROCESSING_FAILED
			),
			Arguments.of(
				new SpeechTranscriptionTimeoutException(),
				HttpStatus.GATEWAY_TIMEOUT,
				ErrorCode.STT_PROCESSING_TIMEOUT
			)
		);
	}

	private static Stream<RuntimeException> invalidRequestExceptions() {
		return Stream.of(
			new IllegalArgumentException("요청 값이 올바르지 않습니다."),
			new IllegalStateException("요청 상태가 올바르지 않습니다.")
		);
	}
}
