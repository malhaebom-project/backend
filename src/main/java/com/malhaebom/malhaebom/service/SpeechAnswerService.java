package com.malhaebom.malhaebom.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;
import com.malhaebom.malhaebom.global.exception.AiRequestLimitExceededException;
import com.malhaebom.malhaebom.global.exception.SpeechNotRecognizedException;
import com.malhaebom.malhaebom.global.exception.SpeechProcessingFailedException;
import com.malhaebom.malhaebom.global.exception.SpeechTranscriptionTimeoutException;
import com.malhaebom.malhaebom.service.model.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.model.SpeechAudio;
import com.malhaebom.malhaebom.service.model.SpeechTranscriptionResult;
import com.malhaebom.malhaebom.service.speech.SpeechTranscriber;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpeechAnswerService {

	private static final String STT_PROVIDER = "AMAZON_TRANSCRIBE";

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

		try {
			SpeechTranscriptionResult result = transcriber.transcribe(
				started.getId(),
				requestKey,
				audio
			);
			validateTranscript(result);

			SpeechAnswer completed = stateService.complete(
				started.getId(),
				result.transcript(),
				result.confidence(),
				result.provider()
			);
			return SpeechAnswerResult.from(completed);
		} catch (SpeechNotRecognizedException exception) {
			stateService.fail(
				started.getId(),
				"인식된 발화가 없습니다.",
				STT_PROVIDER
			);
			throw exception;
		} catch (SpeechTranscriptionTimeoutException exception) {
			stateService.fail(
				started.getId(),
					"STT 처리 시간이 초과되었습니다.",
				STT_PROVIDER
			);
			throw exception;
		} catch (AiRequestLimitExceededException exception) {
			stateService.fail(
				started.getId(),
					"STT 요청 제한을 초과했습니다.",
				STT_PROVIDER
			);
			throw exception;
		} catch (RuntimeException exception) {
			stateService.fail(
				started.getId(),
					"STT 처리에 실패했습니다.",
				STT_PROVIDER
			);
			throw new SpeechProcessingFailedException(exception);
		}
	}

	private void validateTranscript(SpeechTranscriptionResult result) {
		if (
			result == null
				|| result.transcript() == null
				|| result.transcript().isBlank()
		) {
			throw new SpeechNotRecognizedException();
		}
	}
}
