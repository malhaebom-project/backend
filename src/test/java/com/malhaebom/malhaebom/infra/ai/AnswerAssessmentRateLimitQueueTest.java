package com.malhaebom.malhaebom.infra.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.github.bucket4j.TimeMeter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import com.malhaebom.malhaebom.infra.observability.MicrometerAnswerAssessmentMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.MicrometerProviderRateLimitMetricsRecorder;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;
import com.malhaebom.malhaebom.service.exception.AnswerAssessmentOverloadedException;

class AnswerAssessmentRateLimitQueueTest {
	@Test
	void rate_token이_있으면_기존_active_수와_무관하게_모든_요청을_시작한다() {
		QueueFixture fixture = fixture(64);
		List<ControlledTask> tasks = new ArrayList<>();

		for (int index = 0; index < 64; index++) {
			ControlledTask task = controlledTask();
			tasks.add(task);
			fixture.queue().execute(supplier(task));
		}

		assertEquals(64, tasks.stream()
			.mapToInt(task -> task.started().get()).sum());
		assertEquals(0.0, gauge(fixture, "queue.size"));
		tasks.forEach(task -> task.future().complete(assessment()));
	}

	@Test
	void rate가_부족하면_FIFO로_대기하고_refill마다_head를_승격한다() {
		QueueFixture fixture = fixture(2);
		exhaustTokenBucket(fixture);
		ControlledTask first = controlledTask();
		ControlledTask second = controlledTask();
		List<String> starts = new ArrayList<>();
		AnswerAssessmentTask firstResult = fixture.queue().execute(
			named("first", starts, first));
		AnswerAssessmentTask secondResult = fixture.queue().execute(
			named("second", starts, second));

		assertTrue(starts.isEmpty());
		assertEquals(3, fixture.scheduler().scheduledCount());
		assertEquals(1.0, rateCounter(fixture, "delayed"));

		fixture.ticker().addAndGet(Duration.ofMillis(300).toNanos());
		fixture.scheduler().runShortestDelay();
		assertEquals(List.of("first"), starts);
		assertEquals(2.0, rateCounter(fixture, "delayed"));
		assertFalse(firstResult.result().toCompletableFuture().isDone());
		assertFalse(secondResult.result().toCompletableFuture().isDone());

		fixture.ticker().addAndGet(Duration.ofMillis(450).toNanos());
		fixture.scheduler().runShortestDelay();
		assertEquals(List.of("first", "second"), starts);
		first.future().complete(assessment());
		second.future().complete(assessment());
		firstResult.result().toCompletableFuture().join();
		secondResult.result().toCompletableFuture().join();
	}

	@Test
	void rate_queue가_차면_supplier_호출_없이_overload로_거절한다() {
		QueueFixture fixture = fixture(1);
		exhaustTokenBucket(fixture);
		AtomicInteger queuedCalls = new AtomicInteger();
		AtomicInteger rejectedCalls = new AtomicInteger();
		fixture.queue().execute(() -> {
			queuedCalls.incrementAndGet();
			return completedTask();
		});
		AnswerAssessmentTask rejected = fixture.queue().execute(() -> {
			rejectedCalls.incrementAndGet();
			return completedTask();
		});

		assertOverloaded(rejected);
		assertEquals(0, queuedCalls.get());
		assertEquals(0, rejectedCalls.get());
		assertEquals(1.0, counter(fixture, "queue.full"));
		assertEquals(1.0, gauge(fixture, "queue.size"));
	}

	@Test
	void rate_대기_요청을_취소하면_즉시_queue_용량을_반환한다() {
		QueueFixture fixture = fixture(1);
		exhaustTokenBucket(fixture);
		AtomicInteger calls = new AtomicInteger();
		AnswerAssessmentTask queued = fixture.queue().execute(() -> {
			calls.incrementAndGet();
			return completedTask();
		});

		assertTrue(queued.cancel());

		assertEquals(0, calls.get());
		assertTrue(queued.result().toCompletableFuture().isCancelled());
		assertEquals(0.0, gauge(fixture, "queue.size"));
		assertEquals(0, fixture.scheduler().scheduledCount());
		assertEquals(1.0, counter(fixture, "queue.cancelled"));
	}

	@Test
	void rate_대기_10초_timeout은_503과_rate_rejected로_끝난다() {
		QueueFixture fixture = fixture(1);
		exhaustTokenBucket(fixture);
		AtomicInteger calls = new AtomicInteger();
		AnswerAssessmentTask queued = fixture.queue().execute(() -> {
			calls.incrementAndGet();
			return completedTask();
		});

		fixture.ticker().addAndGet(Duration.ofSeconds(10).toNanos());
		fixture.scheduler().runFirstScheduled();

		assertOverloaded(queued);
		assertEquals(0, calls.get());
		assertEquals(1.0, counter(fixture, "queue.timeout"));
		assertEquals(1.0, rateCounter(fixture, "rejected"));
		assertEquals(0, fixture.scheduler().scheduledCount());
	}

	@Test
	void 실행_중인_task_취소는_실제_OpenAI_task에_전파한다() {
		QueueFixture fixture = fixture(1);
		ControlledTask controlled = controlledTask();
		AnswerAssessmentTask task = fixture.queue().execute(
			supplier(controlled));

		assertTrue(task.cancel());

		assertTrue(controlled.cancelled().get());
		assertTrue(task.result().toCompletableFuture().isCompletedExceptionally());
	}

	@Test
	void supplier_동기_실패가_나도_다음_rate_token_요청은_독립적으로_시작한다() {
		QueueFixture fixture = fixture(1);
		RuntimeException failure = new IllegalStateException("supplier");
		AnswerAssessmentTask failed = fixture.queue().execute(() -> {
			throw failure;
		});
		AtomicInteger nextCalls = new AtomicInteger();
		AnswerAssessmentTask next = fixture.queue().execute(() -> {
			nextCalls.incrementAndGet();
			return completedTask();
		});

		assertEquals(failure, completionFailure(failed));
		next.result().toCompletableFuture().join();
		assertEquals(1, nextCalls.get());
	}

	@Test
	void shutdown은_rate_대기와_scheduler를_정리하고_신규_요청을_막는다() {
		QueueFixture fixture = fixture(1);
		exhaustTokenBucket(fixture);
		AtomicInteger calls = new AtomicInteger();
		AnswerAssessmentTask queued = fixture.queue().execute(() -> {
			calls.incrementAndGet();
			return completedTask();
		});

		fixture.queue().shutdown();

		assertInstanceOf(IllegalStateException.class, completionFailure(queued));
		assertEquals(0, fixture.scheduler().scheduledCount());
		AnswerAssessmentTask afterShutdown = fixture.queue().execute(() -> {
			calls.incrementAndGet();
			return completedTask();
		});
		assertInstanceOf(IllegalStateException.class,
			completionFailure(afterShutdown));
		assertEquals(0, calls.get());
	}

	private QueueFixture fixture(int queueCapacity) {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ControllableScheduler scheduler = new ControllableScheduler();
		AtomicLong ticker = new AtomicLong();
		TimeMeter timeMeter = new TimeMeter() {
			@Override
			public long currentTimeNanos() {
				return ticker.get();
			}

			@Override
			public boolean isWallClockBased() {
				return false;
			}
		};
		OpenAiAnswerAssessmentRateLimiter rateLimiter =
			new OpenAiAnswerAssessmentRateLimiter(
				new OpenAiAnswerAssessmentRateLimitProperties(
					400, 400_000, 3_000),
				new MicrometerProviderRateLimitMetricsRecorder(registry),
				timeMeter);
		AnswerAssessmentRateLimitQueue queue =
			new AnswerAssessmentRateLimitQueue(
				new AnswerAssessmentQueueProperties(
					queueCapacity,
					queueCapacity == 0
						? Duration.ZERO : Duration.ofSeconds(10)),
				new MicrometerAnswerAssessmentMetricsRecorder(registry),
				rateLimiter,
				scheduler,
				ticker::get);
		return new QueueFixture(queue, registry, scheduler, ticker);
	}

	private void exhaustTokenBucket(QueueFixture fixture) {
		for (int index = 0; index < 133; index++) {
			fixture.queue().execute(this::completedTask)
				.result().toCompletableFuture().join();
		}
	}

	private Supplier<AnswerAssessmentTask> supplier(ControlledTask task) {
		return () -> {
			task.started().incrementAndGet();
			return task.task();
		};
	}

	private Supplier<AnswerAssessmentTask> named(
		String name,
		List<String> starts,
		ControlledTask task
	) {
		return () -> {
			starts.add(name);
			task.started().incrementAndGet();
			return task.task();
		};
	}

	private ControlledTask controlledTask() {
		CompletableFuture<AnswerAssessment> future = new CompletableFuture<>();
		AtomicBoolean cancelled = new AtomicBoolean();
		AtomicInteger started = new AtomicInteger();
		AnswerAssessmentTask task = new AnswerAssessmentTask(future, () -> {
			cancelled.set(true);
			return future.cancel(true);
		});
		return new ControlledTask(task, future, cancelled, started);
	}

	private AnswerAssessmentTask completedTask() {
		return new AnswerAssessmentTask(
			CompletableFuture.completedFuture(assessment()), () -> false);
	}

	private void assertOverloaded(AnswerAssessmentTask task) {
		assertInstanceOf(
			AnswerAssessmentOverloadedException.class,
			completionFailure(task)
		);
	}

	private Throwable completionFailure(AnswerAssessmentTask task) {
		CompletionException exception = assertThrows(
			CompletionException.class,
			() -> task.result().toCompletableFuture().join());
		return exception.getCause();
	}

	private double gauge(QueueFixture fixture, String suffix) {
		return fixture.registry().get("malhaebom.answer.assessment." + suffix)
			.gauge().value();
	}

	private double counter(QueueFixture fixture, String suffix) {
		return fixture.registry().get("malhaebom.answer.assessment." + suffix)
			.counter().count();
	}

	private double rateCounter(QueueFixture fixture, String result) {
		return fixture.registry()
			.get("malhaebom.ai.provider.rate.limit.requests")
			.tag("provider", "openai")
			.tag("result", result)
			.counter().count();
	}

	private AnswerAssessment assessment() {
		return new AnswerAssessment(true, 50, 30, 20, "정확하게 잘 말했어요!");
	}

	private record QueueFixture(
		AnswerAssessmentRateLimitQueue queue,
		SimpleMeterRegistry registry,
		ControllableScheduler scheduler,
		AtomicLong ticker
	) {
	}

	private record ControlledTask(
		AnswerAssessmentTask task,
		CompletableFuture<AnswerAssessment> future,
		AtomicBoolean cancelled,
		AtomicInteger started
	) {
	}

	private static final class ControllableScheduler
		implements AnswerAssessmentQueueTimeoutScheduler {

		private final Queue<ScheduledTask> tasks = new ArrayDeque<>();

		@Override
		public synchronized TimeoutHandle schedule(
			Runnable task,
			Duration delay
		) {
			ScheduledTask scheduled = new ScheduledTask(task, delay);
			tasks.add(scheduled);
			return () -> cancel(scheduled);
		}

		void runFirstScheduled() {
			ScheduledTask scheduled;
			synchronized (this) {
				scheduled = tasks.poll();
			}
			run(scheduled);
		}

		void runShortestDelay() {
			ScheduledTask scheduled;
			synchronized (this) {
				scheduled = tasks.stream()
					.min(Comparator.comparing(task -> task.delay))
					.orElse(null);
				if (scheduled != null) {
					tasks.remove(scheduled);
				}
			}
			run(scheduled);
		}

		synchronized int scheduledCount() {
			return tasks.size();
		}

		private void run(ScheduledTask scheduled) {
			if (scheduled != null && !scheduled.cancelled) {
				scheduled.task.run();
			}
		}

		private synchronized void cancel(ScheduledTask scheduled) {
			scheduled.cancelled = true;
			tasks.remove(scheduled);
		}

		private static final class ScheduledTask {
			private final Runnable task;
			private final Duration delay;
			private boolean cancelled;

			private ScheduledTask(Runnable task, Duration delay) {
				this.task = task;
				this.delay = delay;
			}
		}
	}
}
