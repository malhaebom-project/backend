package com.malhaebom.malhaebom.service.dto;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class AnswerAssessmentTask {

	private final CompletionStage<AnswerAssessment> result;
	private final BooleanSupplier cancellation;

	public AnswerAssessmentTask(
		CompletionStage<AnswerAssessment> result,
		BooleanSupplier cancellation
	) {
		this.result = Objects.requireNonNull(
			result,
			"채점 작업 결과는 null일 수 없습니다."
		);
		this.cancellation = Objects.requireNonNull(
			cancellation,
			"채점 작업 취소 동작은 null일 수 없습니다."
		);
	}

	public static AnswerAssessmentTask failed(Throwable exception) {
		Objects.requireNonNull(exception, "채점 실패 예외는 null일 수 없습니다.");
		return new AnswerAssessmentTask(
			CompletableFuture.failedFuture(exception),
			() -> false
		);
	}

	public CompletionStage<AnswerAssessment> result() {
		return result;
	}

	public boolean cancel() {
		return cancellation.getAsBoolean();
	}

	public AnswerAssessmentTask map(
		Function<AnswerAssessment, AnswerAssessment> mapper
	) {
		Objects.requireNonNull(mapper, "채점 결과 변환기는 null일 수 없습니다.");
		return new AnswerAssessmentTask(
			result.thenApply(mapper),
			this::cancel
		);
	}

}
