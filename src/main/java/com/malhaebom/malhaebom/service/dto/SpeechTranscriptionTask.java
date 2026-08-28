package com.malhaebom.malhaebom.service.dto;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

public final class SpeechTranscriptionTask {
	private final CompletionStage<SpeechTranscriptionResult> result;
	private final BooleanSupplier cancellation;

	public SpeechTranscriptionTask(
		CompletionStage<SpeechTranscriptionResult> result,
		BooleanSupplier cancellation
	) {
		this.result = Objects.requireNonNull(
			result,
			"음성 변환 작업 결과는 null일 수 없습니다."
		);
		this.cancellation = Objects.requireNonNull(
			cancellation,
			"음성 변환 작업 취소 동작은 null일 수 없습니다."
		);
	}

	public static SpeechTranscriptionTask completed(
		SpeechTranscriptionResult result
	) {
		Objects.requireNonNull(result, "완료된 음성 변환 결과는 null일 수 없습니다.");
		return new SpeechTranscriptionTask(
			CompletableFuture.completedFuture(result),
			() -> false
		);
	}

	public static SpeechTranscriptionTask failed(Throwable exception) {
		Objects.requireNonNull(exception, "음성 변환 실패 예외는 null일 수 없습니다.");
		return new SpeechTranscriptionTask(
			CompletableFuture.failedFuture(exception),
			() -> false
		);
	}

	public CompletionStage<SpeechTranscriptionResult> result() {
		return result;
	}

	public boolean cancel() {
		return cancellation.getAsBoolean();
	}
}
