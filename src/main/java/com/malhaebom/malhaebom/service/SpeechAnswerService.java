package com.malhaebom.malhaebom.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAudio;
import com.malhaebom.malhaebom.service.dto.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpeechAnswerService {

	private final SpeechAnswerStateService stateService;
	private final SpeechTranscriber transcriber;

	public SpeechAnswerResult upload(
		Long sessionId,
		Long sessionQuestionId,
		String requestKey,
		SpeechAudio audio
	) {
		Objects.requireNonNull(audio, "음성 파일은 null일 수 없습니다.");

		SpeechAnswer started = stateService.start(
			sessionId,
			sessionQuestionId,
			requestKey
		);
		if (started.isCompleted()) {
			return SpeechAnswerResult.from(started);
		}
		String provider = transcriber.provider();

		SpeechTranscriptionResult result;
		try {
			result = transcriber.transcribe(audio);
			validateTranscript(result);
		} catch (ApiException exception) {
			recordFailure(started, provider, exception.getErrorCode());
			throw exception;
		} catch (RuntimeException exception) {
			recordFailure(started, provider, ErrorCode.STT_PROCESSING_FAILED);
			throw new ApiException(
				ErrorCode.STT_PROCESSING_FAILED,
				exception
			);
		}

		SpeechAnswer completed = stateService.complete(
			started.getId(),
			result.transcript(),
			result.confidence(),
			provider
		);
		return SpeechAnswerResult.from(completed);
	}

	private void validateTranscript(SpeechTranscriptionResult result) {
		if (
			result == null
				|| result.transcript() == null
				|| result.transcript().isBlank()
		) {
			throw new ApiException(ErrorCode.SPEECH_NOT_RECOGNIZED);
		}
	}

	private void recordFailure(
		SpeechAnswer started,
		String provider,
		ErrorCode errorCode
	) {
		String message = switch (errorCode) {
			case SPEECH_NOT_RECOGNIZED -> "인식된 발화가 없습니다.";
			case STT_PROCESSING_TIMEOUT -> "STT 처리 시간이 초과되었습니다.";
			case AI_REQUEST_LIMIT_EXCEEDED -> "STT 요청 제한을 초과했습니다.";
			default -> "STT 처리에 실패했습니다.";
		};

		stateService.fail(started.getId(), message, provider);
	}
}
