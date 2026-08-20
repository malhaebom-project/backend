package com.malhaebom.malhaebom.service.dto;

import java.util.List;
import java.util.Objects;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;

public record SpeechAnswerStartResult(
	SpeechAnswer speechAnswer,
	List<String> adaptationPhrases,
	SpeechAnswerStartStatus status,
	String processingToken
) {

	public SpeechAnswerStartResult {
		Objects.requireNonNull(
			speechAnswer,
			"음성 답변은 null일 수 없습니다."
		);
		adaptationPhrases = List.copyOf(Objects.requireNonNull(
			adaptationPhrases,
			"적응 문구 목록은 null일 수 없습니다."
		));
		Objects.requireNonNull(status, "음성 답변 시작 상태는 null일 수 없습니다.");
		if (status == SpeechAnswerStartStatus.COMPLETED) {
			if (processingToken != null) {
				throw new IllegalArgumentException("완료된 음성 답변에는 처리 토큰이 없어야 합니다.");
			}
		} else if (processingToken == null || processingToken.isBlank()) {
			throw new IllegalArgumentException("처리 중인 음성 답변에는 처리 토큰이 필요합니다.");
		}
	}

	public static SpeechAnswerStartResult claimed(
		SpeechAnswer speechAnswer,
		List<String> adaptationPhrases
	) {
		return new SpeechAnswerStartResult(
			speechAnswer,
			adaptationPhrases,
			SpeechAnswerStartStatus.CLAIMED,
			speechAnswer.getProcessingToken()
		);
	}

	public static SpeechAnswerStartResult processing(
		SpeechAnswer speechAnswer,
		List<String> adaptationPhrases
	) {
		return new SpeechAnswerStartResult(
			speechAnswer,
			adaptationPhrases,
			SpeechAnswerStartStatus.PROCESSING,
			speechAnswer.getProcessingToken()
		);
	}

	public static SpeechAnswerStartResult completed(
		SpeechAnswer speechAnswer,
		List<String> adaptationPhrases
	) {
		return new SpeechAnswerStartResult(
			speechAnswer,
			adaptationPhrases,
			SpeechAnswerStartStatus.COMPLETED,
			null
		);
	}

	public boolean isClaimed() {
		return status == SpeechAnswerStartStatus.CLAIMED;
	}

	public boolean isProcessing() {
		return status == SpeechAnswerStartStatus.PROCESSING;
	}

	public boolean isCompleted() {
		return status == SpeechAnswerStartStatus.COMPLETED;
	}
}
