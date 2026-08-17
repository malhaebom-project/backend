package com.malhaebom.malhaebom.infra.ai;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;

@Component
public class AnswerAssessmentConcurrencyLimiter {

	private final Semaphore permits;

	public AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties
	) {
		permits = new Semaphore(properties.maxConcurrentRequests());
	}

	public <T> CompletionStage<T> execute(
		Supplier<CompletionStage<T>> task
	) {
		Objects.requireNonNull(task, "제한할 작업은 null일 수 없습니다.");
		if (!permits.tryAcquire()) {
			return CompletableFuture.failedFuture(
				new ApiException(ErrorCode.ANSWER_ASSESSMENT_OVERLOADED)
			);
		}

		CompletionStage<T> stage;
		try {
			stage = Objects.requireNonNull(
				task.get(),
				"제한된 작업은 null을 반환할 수 없습니다."
			);
		} catch (RuntimeException exception) {
			permits.release();
			return CompletableFuture.failedFuture(exception);
		}

		return stage.whenComplete((result, exception) -> permits.release());
	}
}
