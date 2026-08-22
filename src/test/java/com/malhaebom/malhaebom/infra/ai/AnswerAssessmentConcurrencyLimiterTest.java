package com.malhaebom.malhaebom.infra.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;

class AnswerAssessmentConcurrencyLimiterTest {

	@Test
	void 진행_중인_작업과_거절을_계측하고_완료_후_permit을_복구한다() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		AnswerAssessmentConcurrencyLimiter limiter =
			new AnswerAssessmentConcurrencyLimiter(
				new AnswerAssessmentConcurrencyProperties(1),
				registry
			);
		CompletableFuture<AnswerAssessment> pending = new CompletableFuture<>();

		AnswerAssessmentTask accepted = limiter.execute(
			() -> new AnswerAssessmentTask(pending, () -> pending.cancel(true))
		);
		AnswerAssessmentTask rejected = limiter.execute(
			() -> AnswerAssessmentTask.failed(new AssertionError())
		);

		assertEquals(1.0, gauge(registry, "active"));
		assertEquals(1.0, gauge(registry, "limit"));
		assertEquals(1.0, counter(registry, "accepted"));
		assertEquals(1.0, counter(registry, "rejected"));
		CompletionException overloaded = org.junit.jupiter.api.Assertions
			.assertThrows(
				CompletionException.class,
				() -> rejected.result().toCompletableFuture().join()
			);
		ApiException apiException = assertInstanceOf(
			ApiException.class,
			overloaded.getCause()
		);
		assertEquals(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED,
			apiException.getErrorCode()
		);

		pending.complete(assessment());
		accepted.result().toCompletableFuture().join();

		assertEquals(0.0, gauge(registry, "active"));
		assertEquals(1.0, counter(registry, "completed"));
		assertEquals(0.0, counter(registry, "failed"));
		AnswerAssessmentTask recovered = limiter.execute(
			() -> new AnswerAssessmentTask(
				CompletableFuture.completedFuture(assessment()),
				() -> false
			)
		);
		recovered.result().toCompletableFuture().join();
		assertEquals(2.0, counter(registry, "accepted"));
	}

	private double gauge(SimpleMeterRegistry registry, String suffix) {
		return registry.get("malhaebom.answer.assessment." + suffix)
			.gauge()
			.value();
	}

	private double counter(SimpleMeterRegistry registry, String suffix) {
		return registry.get("malhaebom.answer.assessment." + suffix)
			.counter()
			.count();
	}

	private AnswerAssessment assessment() {
		return new AnswerAssessment(
			true,
			50,
			30,
			20,
			"정확하게 잘 말했어요!"
		);
	}
}
