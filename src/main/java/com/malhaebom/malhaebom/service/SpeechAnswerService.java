package com.malhaebom.malhaebom.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerStartResult;
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

		SpeechAnswerStartResult startResult = stateService.start(
			sessionId,
			sessionQuestionId,
			requestKey
		);
		SpeechAnswer started = startResult.speechAnswer();
		if (started.isCompleted()) {
			return SpeechAnswerResult.from(started);
		}
		String provider = transcriber.provider();

		SpeechTranscriptionResult result;
		try {
			result = transcriber.transcribe(
				audio,
				startResult.adaptationPhrases()
			);
			validateTranscript(result);
		} catch (ApiException exception) {
			stateService.fail(
				started.getId(),
				exception.getErrorCode().getMessage(),
				provider
			);
			throw exception;
		} catch (RuntimeException exception) {
			stateService.fail(
				started.getId(),
				ErrorCode.STT_PROCESSING_FAILED.getMessage(),
				provider
			);
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
}
