package com.malhaebom.malhaebom.service.dto;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

public final class AnswerSubmissionTask {

	private final CompletionStage<AnswerSubmissionResult> result;
	private final BooleanSupplier cancellation;

	public AnswerSubmissionTask(
		CompletionStage<AnswerSubmissionResult> result,
		BooleanSupplier cancellation
	) {
		this.result = Objects.requireNonNull(
			result,
			"답변 제출 작업 결과는 null일 수 없습니다."
		);
		this.cancellation = Objects.requireNonNull(
			cancellation,
			"답변 제출 작업 취소 동작은 null일 수 없습니다."
		);
	}

	public static AnswerSubmissionTask completed(
		AnswerSubmissionResult result
	) {
		Objects.requireNonNull(result, "완료된 답변 제출 결과는 null일 수 없습니다.");
		return new AnswerSubmissionTask(
			CompletableFuture.completedFuture(result),
			() -> false
		);
	}

	public CompletionStage<AnswerSubmissionResult> result() {
		return result;
	}

	public boolean cancel() {
		return cancellation.getAsBoolean();
	}
}
