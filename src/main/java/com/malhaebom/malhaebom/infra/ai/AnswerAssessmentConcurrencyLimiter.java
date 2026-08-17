package com.malhaebom.malhaebom.infra.ai;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;

@Component
public class AnswerAssessmentConcurrencyLimiter {

	private final Semaphore permits;

	public AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties
	) {
		permits = new Semaphore(properties.maxConcurrentRequests());
	}

	public AnswerAssessmentTask execute(
		Supplier<AnswerAssessmentTask> taskSupplier
	) {
		Objects.requireNonNull(
			taskSupplier,
			"제한할 작업은 null일 수 없습니다."
		);
		if (!permits.tryAcquire()) {
			return AnswerAssessmentTask.failed(
				new ApiException(ErrorCode.ANSWER_ASSESSMENT_OVERLOADED)
			);
		}

		AnswerAssessmentTask task;
		try {
			task = Objects.requireNonNull(
				taskSupplier.get(),
				"제한된 작업은 null을 반환할 수 없습니다."
			);
		} catch (RuntimeException exception) {
			permits.release();
			return AnswerAssessmentTask.failed(exception);
		}

		task.result().whenComplete(
			(result, exception) -> permits.release()
		);
		return task;
	}
}
