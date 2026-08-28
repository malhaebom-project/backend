package com.malhaebom.malhaebom.service.dto;

import java.util.Objects;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;

public record SpeechAnswerRequest(
	Long userId,
	Long sessionId,
	Long sessionQuestionId,
	String requestKey,
	SpeechAudio audio
) {
	private static final int MAX_REQUEST_KEY_LENGTH = 100;

	public SpeechAnswerRequest {
		Objects.requireNonNull(userId, "사용자 ID는 null일 수 없습니다.");
		Objects.requireNonNull(sessionId, "학습 세션 ID는 null일 수 없습니다.");
		Objects.requireNonNull(sessionQuestionId, "학습 세션 문제 ID는 null일 수 없습니다.");
		Objects.requireNonNull(audio, "음성 파일은 null일 수 없습니다.");

		if (requestKey == null || requestKey.isBlank()) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"중복 요청 방지를 위한 요청 식별 키가 필요합니다."
			);
		}
		if (requestKey.length() > MAX_REQUEST_KEY_LENGTH) {
			throw new ApiException(
				ErrorCode.INVALID_REQUEST,
				"요청 식별 키는 100자를 초과할 수 없습니다."
			);
		}
	}
}
