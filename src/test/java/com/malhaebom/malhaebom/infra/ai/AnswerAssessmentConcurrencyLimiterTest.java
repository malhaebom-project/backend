package com.malhaebom.malhaebom.infra.ai;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;

class AnswerAssessmentConcurrencyLimiterTest {

	@Test
	void active가_차면_supplier를_실행하지_않고_pending_task를_즉시_반환한다() {
		LimiterFixture fixture = fixture(1, 1);
		ControlledTask first = controlledTask();
		AtomicInteger secondCalls = new AtomicInteger();

		AnswerAssessmentTask active = fixture.limiter().execute(supplier(first));
		AnswerAssessmentTask queued = fixture.limiter().execute(() -> {
			secondCalls.incrementAndGet();
			return completedTask();
		});

		assertEquals(0, secondCalls.get());
		assertEquals(1.0, gauge(fixture, "active"));
		assertEquals(1.0, gauge(fixture, "queue.size"));
		assertEquals(1.0, counter(fixture, "accepted"));
		assertEquals(1.0, counter(fixture, "queued"));
		assertEquals(0.0, counter(fixture, "queue.promoted"));
		assertFalse(queued.result().toCompletableFuture().isDone());

		first.future().complete(assessment());
		active.result().toCompletableFuture().join();
		queued.result().toCompletableFuture().join();
		assertEquals(1, secondCalls.get());
		assertEquals(2.0, counter(fixture, "accepted"));
		assertEquals(1.0, counter(fixture, "queue.promoted"));
		assertEquals(
			1L,
			fixture.registry().get("malhaebom.answer.assessment.queue.wait")
				.tag("result", "promoted")
				.timer()
				.count()
		);
		assertEquals(0.0, gauge(fixture, "active"));
	}

	@Test
	void active와_queue가_모두_차면_supplier_호출_없이_overload로_거절한다() {
		LimiterFixture fixture = fixture(1, 1);
		ControlledTask first = controlledTask();
		AtomicInteger queuedCalls = new AtomicInteger();
		AtomicInteger rejectedCalls = new AtomicInteger();

		fixture.limiter().execute(supplier(first));
		fixture.limiter().execute(() -> {
			queuedCalls.incrementAndGet();
			return completedTask();
		});
		AnswerAssessmentTask rejected = fixture.limiter().execute(() -> {
			rejectedCalls.incrementAndGet();
			return completedTask();
		});

		assertOverloaded(rejected);
		assertEquals(0, queuedCalls.get());
		assertEquals(0, rejectedCalls.get());
		assertEquals(1.0, counter(fixture, "rejected"));
		assertEquals(1.0, counter(fixture, "queue.full"));
		assertEquals(0.0, counter(fixture, "queue.timeout"));
		assertEquals(1.0, gauge(fixture, "queue.size"));
		assertEquals(1.0, gauge(fixture, "queue.capacity"));

		first.future().complete(assessment());
	}

	@Test
	void 완료된_active_자리는_FIFO로_직접_넘겨_늦은_요청의_추월을_막는다() {
		LimiterFixture fixture = fixture(1, 2);
		ControlledTask active = controlledTask();
		ControlledTask firstQueued = controlledTask();
		ControlledTask secondQueued = controlledTask();
		ControlledTask late = controlledTask();
		List<String> starts = new ArrayList<>();

		fixture.limiter().execute(supplier(active));
		fixture.limiter().execute(named("first", starts, firstQueued));
		fixture.limiter().execute(named("second", starts, secondQueued));

		active.future().complete(assessment());
		fixture.limiter().execute(named("late", starts, late));
		assertEquals(List.of("first"), starts);

		firstQueued.future().complete(assessment());
		assertEquals(List.of("first", "second"), starts);
		secondQueued.future().complete(assessment());
		assertEquals(List.of("first", "second", "late"), starts);
		late.future().complete(assessment());

		assertEquals(0.0, gauge(fixture, "active"));
		assertEquals(0.0, gauge(fixture, "queue.size"));
	}

	@Test
	void queued_task를_취소하면_즉시_용량을_반환하고_supplier를_실행하지_않는다() {
		LimiterFixture fixture = fixture(1, 1);
		ControlledTask active = controlledTask();
		AtomicInteger queuedCalls = new AtomicInteger();
		fixture.limiter().execute(supplier(active));
		AnswerAssessmentTask queued = fixture.limiter().execute(() -> {
			queuedCalls.incrementAndGet();
			return completedTask();
		});

		assertTrue(queued.cancel());
		assertTrue(queued.result().toCompletableFuture().isCancelled());
		assertEquals(0.0, gauge(fixture, "queue.size"));
		assertEquals(0, fixture.scheduler().scheduledCount());
		assertEquals(1.0, counter(fixture, "queue.cancelled"));
		assertEquals(
			1L,
			fixture.registry().get("malhaebom.answer.assessment.queue.wait")
				.tag("result", "cancelled")
				.timer()
				.count()
		);

		active.future().complete(assessment());
		assertEquals(0, queuedCalls.get());
		assertEquals(0.0, gauge(fixture, "active"));
	}

	@Test
	void queue_wait_timeout은_overload로_끝나고_나중에도_supplier를_실행하지_않는다() {
		LimiterFixture fixture = fixture(1, 1);
		ControlledTask active = controlledTask();
		AtomicInteger queuedCalls = new AtomicInteger();
		fixture.limiter().execute(supplier(active));
		AnswerAssessmentTask queued = fixture.limiter().execute(() -> {
			queuedCalls.incrementAndGet();
			return completedTask();
		});

		fixture.ticker().addAndGet(Duration.ofSeconds(10).toNanos());
		fixture.scheduler().runNext();

		assertOverloaded(queued);
		assertEquals(0.0, gauge(fixture, "queue.size"));
		assertEquals(1.0, counter(fixture, "queue.timeout"));
		assertEquals(1.0, counter(fixture, "rejected"));
		assertEquals(
			Duration.ofSeconds(10).toNanos(),
			(long) fixture.registry()
				.get("malhaebom.answer.assessment.queue.wait")
				.tag("result", "timeout")
				.timer()
				.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)
		);

		active.future().complete(assessment());
		assertEquals(0, queuedCalls.get());
	}

	@Test
	void promotion과_cancel이_경합해도_supplier와_active_반환은_한_번뿐이다()
		throws Exception {
		LimiterFixture fixture = fixture(1, 1);
		ControlledTask active = controlledTask();
		ControlledTask promoted = controlledTask();
		AtomicInteger promotedCalls = new AtomicInteger();
		fixture.limiter().execute(supplier(active));
		AnswerAssessmentTask queued = fixture.limiter().execute(() -> {
			promotedCalls.incrementAndGet();
			return promoted.task();
		});

		runConcurrently(
			() -> active.future().complete(assessment()),
			queued::cancel
		);
		if (!promoted.future().isDone()) {
			promoted.future().complete(assessment());
		}

		assertTrue(promotedCalls.get() <= 1);
		assertEquals(0.0, gauge(fixture, "active"));
		assertEquals(0.0, gauge(fixture, "queue.size"));
		assertEquals(
			promotedCalls.get(),
			(int) counter(fixture, "queue.promoted")
		);
	}

	@Test
	void promotion과_timeout이_경합해도_종료_원인과_supplier_실행은_하나뿐이다()
		throws Exception {
		LimiterFixture fixture = fixture(1, 1);
		ControlledTask active = controlledTask();
		ControlledTask promoted = controlledTask();
		AtomicInteger promotedCalls = new AtomicInteger();
		fixture.limiter().execute(supplier(active));
		AnswerAssessmentTask queued = fixture.limiter().execute(() -> {
			promotedCalls.incrementAndGet();
			return promoted.task();
		});

		runConcurrently(
			() -> active.future().complete(assessment()),
			fixture.scheduler()::runNext
		);
		if (promotedCalls.get() == 1) {
			promoted.future().complete(assessment());
			queued.result().toCompletableFuture().join();
		} else {
			assertOverloaded(queued);
		}

		assertTrue(promotedCalls.get() <= 1);
		assertEquals(0.0, gauge(fixture, "active"));
		assertEquals(0.0, gauge(fixture, "queue.size"));
		assertEquals(
			1.0,
			counter(fixture, "queue.promoted")
				+ counter(fixture, "queue.timeout")
		);
	}

	@Test
	void active_task_취소를_실제_task에_전파하고_다음_요청을_승격한다() {
		LimiterFixture fixture = fixture(1, 1);
		ControlledTask active = controlledTask();
		ControlledTask queuedActive = controlledTask();
		AtomicInteger queuedCalls = new AtomicInteger();
		AnswerAssessmentTask first = fixture.limiter().execute(supplier(active));
		AnswerAssessmentTask queued = fixture.limiter().execute(() -> {
			queuedCalls.incrementAndGet();
			return queuedActive.task();
		});

		assertTrue(first.cancel());
		assertTrue(active.cancelled().get());
		assertEquals(1, queuedCalls.get());
		queuedActive.future().complete(assessment());
		queued.result().toCompletableFuture().join();

		assertEquals(0.0, gauge(fixture, "active"));
		assertEquals(1.0, counter(fixture, "failed"));
		assertEquals(1.0, counter(fixture, "completed"));
	}

	@Test
	void supplier_예외_null과_active_예외_후에도_자리를_복구한다() {
		LimiterFixture fixture = fixture(1, 1);
		RuntimeException supplierFailure = new IllegalStateException("supplier");
		AnswerAssessmentTask thrown = fixture.limiter().execute(() -> {
			throw supplierFailure;
		});
		AnswerAssessmentTask nullTask = fixture.limiter().execute(() -> null);
		ControlledTask exceptional = controlledTask();
		AnswerAssessmentTask failedActive = fixture.limiter().execute(
			supplier(exceptional)
		);
		exceptional.future().completeExceptionally(
			new IllegalStateException("active")
		);

		assertSameCause(thrown, supplierFailure);
		assertInstanceOf(
			NullPointerException.class,
			completionFailure(nullTask)
		);
		assertInstanceOf(
			IllegalStateException.class,
			completionFailure(failedActive)
		);
		assertEquals(0.0, gauge(fixture, "active"));
		assertEquals(3.0, counter(fixture, "accepted"));
		assertEquals(3.0, counter(fixture, "failed"));

		fixture.limiter().execute(this::completedTask)
			.result()
			.toCompletableFuture()
			.join();
		assertEquals(0.0, gauge(fixture, "active"));
	}

	@Test
	void promoted_supplier가_동기_실패해도_다음_FIFO_요청을_계속_승격한다() {
		LimiterFixture fixture = fixture(1, 2);
		ControlledTask active = controlledTask();
		RuntimeException failure = new IllegalStateException("promoted");
		fixture.limiter().execute(supplier(active));
		AnswerAssessmentTask failed = fixture.limiter().execute(() -> {
			throw failure;
		});
		AnswerAssessmentTask recovered = fixture.limiter().execute(
			this::completedTask
		);

		active.future().complete(assessment());

		assertSameCause(failed, failure);
		recovered.result().toCompletableFuture().join();
		assertEquals(3.0, counter(fixture, "accepted"));
		assertEquals(2.0, counter(fixture, "queue.promoted"));
		assertEquals(1.0, counter(fixture, "failed"));
		assertEquals(2.0, counter(fixture, "completed"));
		assertEquals(0.0, gauge(fixture, "active"));
		assertEquals(0.0, gauge(fixture, "queue.size"));
	}

	@Test
	void 여러_요청과_취소가_섞여도_capacity를_넘지_않고_최종_상태를_비운다() {
		LimiterFixture fixture = fixture(2, 3);
		ControlledTask activeOne = controlledTask();
		ControlledTask activeTwo = controlledTask();
		ControlledTask queuedOne = controlledTask();
		ControlledTask cancelled = controlledTask();
		ControlledTask queuedThree = controlledTask();
		ControlledTask replacement = controlledTask();

		fixture.limiter().execute(supplier(activeOne));
		fixture.limiter().execute(supplier(activeTwo));
		fixture.limiter().execute(supplier(queuedOne));
		AnswerAssessmentTask cancelledTask = fixture.limiter().execute(
			supplier(cancelled)
		);
		fixture.limiter().execute(supplier(queuedThree));
		assertBounds(fixture, 2, 3);

		assertTrue(cancelledTask.cancel());
		fixture.limiter().execute(supplier(replacement));
		assertBounds(fixture, 2, 3);

		activeOne.future().complete(assessment());
		activeTwo.future().complete(assessment());
		queuedOne.future().complete(assessment());
		queuedThree.future().complete(assessment());
		replacement.future().complete(assessment());

		assertEquals(0, cancelled.started().get());
		assertEquals(0.0, gauge(fixture, "active"));
		assertEquals(0.0, gauge(fixture, "queue.size"));
		assertEquals(5.0, counter(fixture, "accepted"));
		assertEquals(3.0, counter(fixture, "queue.promoted"));
	}

	@Test
	void queue_capacity_0이면_기존처럼_한도_초과를_즉시_거절한다() {
		LimiterFixture fixture = fixture(1, 0);
		ControlledTask active = controlledTask();
		AtomicInteger rejectedCalls = new AtomicInteger();
		fixture.limiter().execute(supplier(active));

		AnswerAssessmentTask rejected = fixture.limiter().execute(() -> {
			rejectedCalls.incrementAndGet();
			return completedTask();
		});

		assertOverloaded(rejected);
		assertEquals(0, rejectedCalls.get());
		assertEquals(0.0, gauge(fixture, "queue.capacity"));
		assertEquals(1.0, counter(fixture, "queue.full"));
		active.future().complete(assessment());
	}

	@Test
	void shutdown은_queued_task와_timeout을_정리하고_신규_admission을_막는다() {
		LimiterFixture fixture = fixture(1, 1);
		ControlledTask active = controlledTask();
		AtomicInteger queuedCalls = new AtomicInteger();
		fixture.limiter().execute(supplier(active));
		AnswerAssessmentTask queued = fixture.limiter().execute(() -> {
			queuedCalls.incrementAndGet();
			return completedTask();
		});

		fixture.limiter().shutdown();

		assertInstanceOf(IllegalStateException.class, completionFailure(queued));
		assertEquals(0.0, gauge(fixture, "queue.size"));
		assertEquals(0, fixture.scheduler().scheduledCount());
		AtomicInteger afterShutdownCalls = new AtomicInteger();
		AnswerAssessmentTask afterShutdown = fixture.limiter().execute(() -> {
			afterShutdownCalls.incrementAndGet();
			return completedTask();
		});
		assertInstanceOf(
			IllegalStateException.class,
			completionFailure(afterShutdown)
		);
		assertEquals(0, afterShutdownCalls.get());
		assertEquals(0, queuedCalls.get());

		active.future().complete(assessment());
		assertEquals(0.0, gauge(fixture, "active"));
	}

	private LimiterFixture fixture(int activeLimit, int queueCapacity) {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		ControllableTimeoutScheduler scheduler =
			new ControllableTimeoutScheduler();
		AtomicLong ticker = new AtomicLong();
		AnswerAssessmentConcurrencyLimiter limiter =
			new AnswerAssessmentConcurrencyLimiter(
				new AnswerAssessmentConcurrencyProperties(
					activeLimit,
					queueCapacity,
					queueCapacity == 0
						? Duration.ZERO
						: Duration.ofSeconds(10)
				),
				registry,
				scheduler,
				ticker::get
			);
		return new LimiterFixture(limiter, registry, scheduler, ticker);
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

	private Supplier<AnswerAssessmentTask> supplier(ControlledTask task) {
		return () -> {
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
			CompletableFuture.completedFuture(assessment()),
			() -> false
		);
	}

	private void runConcurrently(Runnable first, Runnable second)
		throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CyclicBarrier barrier = new CyclicBarrier(2);
		try {
			Future<?> firstResult = executor.submit(() -> {
				await(barrier);
				first.run();
			});
			Future<?> secondResult = executor.submit(() -> {
				await(barrier);
				second.run();
			});
			firstResult.get(5, SECONDS);
			secondResult.get(5, SECONDS);
		} finally {
			executor.shutdownNow();
			assertTrue(executor.awaitTermination(5, SECONDS));
		}
	}

	private void await(CyclicBarrier barrier) {
		try {
			barrier.await(5, SECONDS);
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private void assertBounds(
		LimiterFixture fixture,
		int activeLimit,
		int queueCapacity
	) {
		assertTrue(gauge(fixture, "active") <= activeLimit);
		assertTrue(gauge(fixture, "queue.size") <= queueCapacity);
		assertTrue(fixture.scheduler().scheduledCount() <= queueCapacity);
	}

	private void assertOverloaded(AnswerAssessmentTask task) {
		ApiException exception = assertInstanceOf(
			ApiException.class,
			completionFailure(task)
		);
		assertEquals(
			ErrorCode.ANSWER_ASSESSMENT_OVERLOADED,
			exception.getErrorCode()
		);
	}

	private void assertSameCause(
		AnswerAssessmentTask task,
		RuntimeException expected
	) {
		assertEquals(expected, completionFailure(task));
	}

	private Throwable completionFailure(AnswerAssessmentTask task) {
		CompletionException exception = assertThrows(
			CompletionException.class,
			() -> task.result().toCompletableFuture().join()
		);
		return exception.getCause();
	}

	private double gauge(LimiterFixture fixture, String suffix) {
		return fixture.registry()
			.get("malhaebom.answer.assessment." + suffix)
			.gauge()
			.value();
	}

	private double counter(LimiterFixture fixture, String suffix) {
		return fixture.registry()
			.get("malhaebom.answer.assessment." + suffix)
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

	private record LimiterFixture(
		AnswerAssessmentConcurrencyLimiter limiter,
		SimpleMeterRegistry registry,
		ControllableTimeoutScheduler scheduler,
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

	private static final class ControllableTimeoutScheduler
		implements AnswerAssessmentQueueTimeoutScheduler {

		private final Queue<ScheduledTask> tasks = new ArrayDeque<>();

		@Override
		public synchronized TimeoutHandle schedule(
			Runnable task,
			Duration delay
		) {
			ScheduledTask scheduled = new ScheduledTask(task);
			tasks.add(scheduled);
			return () -> cancel(scheduled);
		}

		void runNext() {
			ScheduledTask scheduled;
			synchronized (this) {
				scheduled = tasks.poll();
			}
			if (scheduled != null && !scheduled.cancelled) {
				scheduled.task.run();
			}
		}

		synchronized int scheduledCount() {
			return tasks.size();
		}

		private synchronized void cancel(ScheduledTask scheduled) {
			scheduled.cancelled = true;
			tasks.remove(scheduled);
		}

		private static final class ScheduledTask {

			private final Runnable task;
			private boolean cancelled;

			private ScheduledTask(Runnable task) {
				this.task = task;
			}
		}
	}
}
