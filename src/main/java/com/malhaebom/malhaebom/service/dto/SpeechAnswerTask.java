package com.malhaebom.malhaebom.service.dto;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

public final class SpeechAnswerTask {
	private final CompletionStage<SpeechAnswerResult> result;
	private final BooleanSupplier cancellation;

	public SpeechAnswerTask(
		CompletionStage<SpeechAnswerResult> result,
		BooleanSupplier cancellation
	) {
		this.result = Objects.requireNonNull(
			result,
			"음성 답변 작업 결과는 null일 수 없습니다."
		);
		this.cancellation = Objects.requireNonNull(
			cancellation,
			"음성 답변 작업 취소 동작은 null일 수 없습니다."
		);
	}

	public static SpeechAnswerTask completed(SpeechAnswerResult result) {
		Objects.requireNonNull(result, "완료된 음성 답변 결과는 null일 수 없습니다.");
		return new SpeechAnswerTask(
			CompletableFuture.completedFuture(result),
			() -> false
		);
	}

	public static SpeechAnswerTask failed(Throwable exception) {
		Objects.requireNonNull(exception, "음성 답변 실패 예외는 null일 수 없습니다.");
		return new SpeechAnswerTask(
			CompletableFuture.failedFuture(exception),
			() -> false
		);
	}

	public CompletionStage<SpeechAnswerResult> result() {
		return result;
	}

	public boolean cancel() {
		return cancellation.getAsBoolean();
	}
}
