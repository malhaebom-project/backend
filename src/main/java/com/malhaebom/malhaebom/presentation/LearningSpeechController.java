package com.malhaebom.malhaebom.presentation;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.infra.openapi.AuthenticatedErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.SpeechProcessingErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.ValidationErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorResponses;
import com.malhaebom.malhaebom.infra.openapi.DomainErrorExample;
import com.malhaebom.malhaebom.infra.openapi.SuccessfulResponse;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.config.SpeechRequestTimeout;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.SpeechAnswerResponse;
import com.malhaebom.malhaebom.service.dto.LoginUser;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerRequest;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerTask;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.speech.SpeechAnswerCoordinator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/learning-sessions")
@RequiredArgsConstructor
@Tag(name = "학습 세션")
@SecurityRequirement(name = "bearerAuth")
@AuthenticatedErrorResponses
public class LearningSpeechController {
	private static final long MAX_AUDIO_FILE_SIZE = 5L * 1024 * 1024;
	private final SpeechAnswerCoordinator speechAnswerCoordinator;
	private final SpeechRequestTimeout requestTimeout;

	@PostMapping(
		path = "/{sessionId}/questions/{sessionQuestionId}/speech",
		consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	@Operation(
		summary = "음성 답변 업로드",
		description = """
			녹음 파일을 업로드하고 STT 결과가 완료될 때까지 같은 HTTP 요청으로 기다립니다.

			- 지원 형식: `audio/webm`, `audio/mp4`, `audio/mpeg`
			- 파일 크기: 최대 5MB
			- 처리 제한시간: 20초. 초과 시 `504 STT_PROCESSING_TIMEOUT`
			- 과부하 시 대기열에 넣지 않고 `503 STT_PROCESSING_OVERLOADED`로 즉시 거절
			- 같은 녹음의 네트워크 재전송에는 같은 `Idempotency-Key` 사용
			- 새로 녹음한 답변에는 새로운 `Idempotency-Key` 사용
			"""
	)
	@SuccessfulResponse(description = "음성 답변 업로드 및 STT 처리 성공")
	@ValidationErrorResponses
	@SpeechProcessingErrorResponses
	@DomainErrorResponses(
		value = {
			ErrorCode.CURRENT_QUESTION_MISMATCH,
			ErrorCode.CHILD_PROFILE_NOT_FOUND,
			ErrorCode.LEARNING_SESSION_NOT_IN_PROGRESS
		},
		examples = {
			@DomainErrorExample(code = ErrorCode.INVALID_AUDIO_FILE, message = "음성 파일을 읽을 수 없습니다.", name = "AUDIO_READ_FAILED"),
			@DomainErrorExample(code = ErrorCode.INVALID_AUDIO_FILE, message = "음성 파일은 비어 있을 수 없습니다.", name = "EMPTY_AUDIO_FILE"),
			@DomainErrorExample(code = ErrorCode.INVALID_AUDIO_FILE, message = "음성 파일은 5MB를 초과할 수 없습니다.", name = "AUDIO_FILE_TOO_LARGE"),
			@DomainErrorExample(code = ErrorCode.INVALID_AUDIO_FILE, message = "지원하지 않는 음성 파일 형식입니다.", name = "UNSUPPORTED_AUDIO_TYPE"),
			@DomainErrorExample(code = ErrorCode.INVALID_REQUEST, message = "중복 요청 방지를 위한 요청 식별 키가 필요합니다.", name = "IDEMPOTENCY_KEY_REQUIRED"),
			@DomainErrorExample(code = ErrorCode.INVALID_REQUEST, message = "요청 식별 키는 100자를 초과할 수 없습니다.", name = "IDEMPOTENCY_KEY_TOO_LONG"),
			@DomainErrorExample(code = ErrorCode.SPEECH_PROCESSING, message = "음성 답변 처리 권한이 만료되었습니다.", name = "SPEECH_PROCESSING_LEASE_EXPIRED")
		}
	)
	public DeferredResult<ApiResponse<SpeechAnswerResponse>> upload(
		@Auth LoginUser loginUser,
		@PathVariable Long sessionId,
		@PathVariable Long sessionQuestionId,
		@RequestHeader(
			value = "Idempotency-Key",
			required = false
		)
		@Parameter(
			description = "같은 녹음 요청의 중복 처리를 방지하는 식별 키. 1~100자이며 UUID 사용을 권장합니다.",
			required = true,
			example = "550e8400-e29b-41d4-a716-446655440000"
		)
		String requestKey,
		@RequestPart(value = "audio", required = false)
		@Parameter(
			description = "STT로 변환할 음성 파일 (webm, mp4 또는 mp3, 최대 5MB)",
			required = true,
			schema = @Schema(type = "string", format = "binary")
		)
		MultipartFile audio
	) {
		SpeechAudio speechAudio = toSpeechAudio(audio);
		SpeechAnswerTask task = speechAnswerCoordinator.uploadAsync(
			new SpeechAnswerRequest(
				loginUser.userId(),
				sessionId,
				sessionQuestionId,
				requestKey,
				speechAudio
			)
		);
		DeferredResult<ApiResponse<SpeechAnswerResponse>> response =
			new DeferredResult<>(requestTimeout.value().toMillis());

		task.result().whenComplete((result, exception) -> {
			if (exception != null) {
				response.setErrorResult(exception);
				return;
			}
			response.setResult(ApiResponse.success(
				SpeechAnswerResponse.from(result),
				"음성 변환이 완료되었습니다."
			));
		});
		response.onTimeout(() -> {
			task.cancel();
			response.setErrorResult(new ApiException(
				ErrorCode.STT_PROCESSING_TIMEOUT
			));
		});
		response.onError(ignored -> task.cancel());
		return response;
	}

	private SpeechAudio toSpeechAudio(MultipartFile audio) {
		validateAudio(audio);

		try {
			return new SpeechAudio(
				audio.getBytes(),
				normalizeContentType(audio.getContentType())
			);
		} catch (IOException exception) {
			throw new ApiException(
				ErrorCode.INVALID_AUDIO_FILE,
				"음성 파일을 읽을 수 없습니다."
			);
		}
	}

	private void validateAudio(MultipartFile audio) {
		if (audio == null || audio.isEmpty()) {
			throw new ApiException(
				ErrorCode.INVALID_AUDIO_FILE,
				"음성 파일은 비어 있을 수 없습니다."
			);
		}

		if (audio.getSize() > MAX_AUDIO_FILE_SIZE) {
			throw new ApiException(
				ErrorCode.INVALID_AUDIO_FILE,
				"음성 파일은 5MB를 초과할 수 없습니다."
			);
		}

		if (!isAllowedContentType(audio.getContentType())) {
			throw new ApiException(
				ErrorCode.INVALID_AUDIO_FILE,
				"지원하지 않는 음성 파일 형식입니다."
			);
		}
	}

	private boolean isAllowedContentType(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return false;
		}

		try {
			MediaType mediaType = MediaType.parseMediaType(contentType);

			return hasTypeAndSubtype(mediaType, "audio", "webm")
				|| hasTypeAndSubtype(mediaType, "audio", "mp4")
				|| hasTypeAndSubtype(mediaType, "audio", "mpeg");
		} catch (InvalidMediaTypeException exception) {
			return false;
		}
	}

	private boolean hasTypeAndSubtype(MediaType mediaType, String type, String subtype) {
		return type.equalsIgnoreCase(mediaType.getType())
			&& subtype.equalsIgnoreCase(mediaType.getSubtype());
	}

	private String normalizeContentType(String contentType) {
		if (contentType == null) {
			return "";
		}

		return contentType
			.toLowerCase(Locale.ROOT)
			.replace(" ", "");
	}
}
