package com.malhaebom.malhaebom.service.dto;

import java.util.List;
import java.util.Objects;

import com.malhaebom.malhaebom.domain.learning.SpeechAnswer;

public record SpeechAnswerStartResult(
	SpeechAnswer speechAnswer,
	List<String> adaptationPhrases
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
	}
}
