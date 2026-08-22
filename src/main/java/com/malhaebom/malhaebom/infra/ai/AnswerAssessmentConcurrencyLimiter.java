package com.malhaebom.malhaebom.infra.ai;

import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;

@Component
public class AnswerAssessmentConcurrencyLimiter {

	private final Semaphore permits;
	private final int maxConcurrentRequests;
	private final AtomicInteger activeRequests = new AtomicInteger();
	private final Counter acceptedRequests;
	private final Counter rejectedRequests;
	private final Counter completedRequests;
	private final Counter failedRequests;

	public AnswerAssessmentConcurrencyLimiter(
		AnswerAssessmentConcurrencyProperties properties,
		MeterRegistry meterRegistry
	) {
		maxConcurrentRequests = properties.maxConcurrentRequests();
		permits = new Semaphore(maxConcurrentRequests);
		acceptedRequests = meterRegistry.counter(
			"malhaebom.answer.assessment.accepted"
		);
		rejectedRequests = meterRegistry.counter(
			"malhaebom.answer.assessment.rejected"
		);
		completedRequests = meterRegistry.counter(
			"malhaebom.answer.assessment.completed"
		);
		failedRequests = meterRegistry.counter(
			"malhaebom.answer.assessment.failed"
		);
		Gauge.builder(
			"malhaebom.answer.assessment.active",
			activeRequests,
			AtomicInteger::get
		).register(meterRegistry);
		Gauge.builder(
			"malhaebom.answer.assessment.limit",
			this,
			limiter -> limiter.maxConcurrentRequests
		).register(meterRegistry);
	}

	public AnswerAssessmentTask execute(
		Supplier<AnswerAssessmentTask> taskSupplier
	) {
		Objects.requireNonNull(
			taskSupplier,
			"제한할 작업은 null일 수 없습니다."
		);
		if (!permits.tryAcquire()) {
			rejectedRequests.increment();
			return AnswerAssessmentTask.failed(
				new ApiException(ErrorCode.ANSWER_ASSESSMENT_OVERLOADED)
			);
		}
		activeRequests.incrementAndGet();
		acceptedRequests.increment();

		AnswerAssessmentTask task;
		try {
			task = Objects.requireNonNull(
				taskSupplier.get(),
				"제한된 작업은 null을 반환할 수 없습니다."
			);
		} catch (RuntimeException exception) {
			releasePermit();
			failedRequests.increment();
			return AnswerAssessmentTask.failed(exception);
		}

		task.result().whenComplete((result, exception) -> {
			releasePermit();
			if (exception == null) {
				completedRequests.increment();
				return;
			}
			failedRequests.increment();
		});
		return task;
	}

	private void releasePermit() {
		activeRequests.decrementAndGet();
		permits.release();
	}
}
