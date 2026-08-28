package com.malhaebom.malhaebom.presentation;

import java.io.IOException;
import java.util.Locale;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.presentation.config.SpeechRequestTimeout;
import com.malhaebom.malhaebom.presentation.auth.Auth;
import com.malhaebom.malhaebom.presentation.dto.ApiResponse;
import com.malhaebom.malhaebom.presentation.dto.SpeechAnswerResponse;
import com.malhaebom.malhaebom.service.SpeechAnswerService;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerRequest;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerTask;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/learning-sessions")
@RequiredArgsConstructor
public class LearningSpeechController {

	private static final long MAX_AUDIO_FILE_SIZE = 5L * 1024 * 1024;
	private final SpeechAnswerService speechAnswerService;
	private final SpeechRequestTimeout requestTimeout;

	@PostMapping(
		path = "/{sessionId}/questions/{sessionQuestionId}/speech",
		consumes = MediaType.MULTIPART_FORM_DATA_VALUE
	)
	public DeferredResult<ApiResponse<SpeechAnswerResponse>> upload(
		@Auth LoginUser loginUser,
		@PathVariable Long sessionId,
		@PathVariable Long sessionQuestionId,
		@RequestHeader(
			value = "Idempotency-Key",
			required = false
		) String requestKey,
		@RequestPart(value = "audio", required = false) MultipartFile audio
	) {
		SpeechAudio speechAudio = toSpeechAudio(audio);
		SpeechAnswerTask task = speechAnswerService.uploadAsync(
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

	private boolean hasTypeAndSubtype(
		MediaType mediaType,
		String type,
		String subtype
	) {
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
