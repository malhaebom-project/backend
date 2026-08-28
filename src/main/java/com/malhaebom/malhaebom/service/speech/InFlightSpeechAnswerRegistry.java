package com.malhaebom.malhaebom.service.speech;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.global.exception.ApiException;
import com.malhaebom.malhaebom.global.exception.ErrorCode;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerStartResult;
import com.malhaebom.malhaebom.service.dto.SpeechAnswerTask;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class InFlightSpeechAnswerRegistry {

	private static final int REQUEST_LOCK_COUNT = 64;

	private final ConcurrentMap<String, InFlightSpeechAnswerTask> tasks =
		new ConcurrentHashMap<>();
	private final ReentrantLock[] requestLocks = createRequestLocks();

	public <T> T withRequestLock(String requestKey, Supplier<T> operation) {
		Objects.requireNonNull(operation, "진행 작업 동작은 null일 수 없습니다.");
		ReentrantLock requestLock = requestLock(requestKey);
		requestLock.lock();
		try {
			return operation.get();
		} finally {
			requestLock.unlock();
		}
	}

	public SpeechAnswerTask join(
		SpeechAnswerStartResult startResult,
		String requestKey
	) {
		InFlightSpeechAnswerTask inFlight = tasks.get(requestKey);
		if (inFlight == null || !inFlight.matches(
			startResult.speechAnswer().getId(),
			startResult.processingToken()
		)) {
			throw new ApiException(ErrorCode.SPEECH_PROCESSING);
		}

		log.info(
			"event=stt_rejoined speech_answer_id={} subscribers={}",
			startResult.speechAnswer().getId(),
			inFlight.subscriberCount() + 1
		);
		return inFlight.subscribe();
	}

	public SpeechAnswerTask register(
		String requestKey,
		SpeechAnswerStartResult startResult,
		SpeechAnswerTask sharedTask
	) {
		InFlightSpeechAnswerTask inFlight = new InFlightSpeechAnswerTask(
			startResult.speechAnswer().getId(),
			startResult.processingToken(),
			sharedTask
		);
		InFlightSpeechAnswerTask previous = tasks.put(requestKey, inFlight);
		sharedTask.result().whenComplete((result, exception) ->
			tasks.remove(requestKey, inFlight)
		);
		if (previous != null) {
			previous.expire();
		}
		return inFlight.subscribe();
	}

	public List<SpeechAnswerTask> snapshot() {
		return tasks.values().stream()
			.map(InFlightSpeechAnswerTask::sharedTask)
			.toList();
	}

	private ReentrantLock requestLock(String requestKey) {
		int hash = requestKey.hashCode();
		return requestLocks[(hash & Integer.MAX_VALUE) % requestLocks.length];
	}

	private static ReentrantLock[] createRequestLocks() {
		ReentrantLock[] locks = new ReentrantLock[REQUEST_LOCK_COUNT];
		for (int index = 0; index < locks.length; index++) {
			locks[index] = new ReentrantLock();
		}
		return locks;
	}

	private static final class InFlightSpeechAnswerTask {

		private final Long speechAnswerId;
		private final String processingToken;
		private final SpeechAnswerTask sharedTask;
		private final AtomicInteger subscribers = new AtomicInteger();

		private InFlightSpeechAnswerTask(
			Long speechAnswerId,
			String processingToken,
			SpeechAnswerTask sharedTask
		) {
			this.speechAnswerId = speechAnswerId;
			this.processingToken = processingToken;
			this.sharedTask = sharedTask;
		}

		private boolean matches(Long speechAnswerId, String processingToken) {
			return Objects.equals(this.speechAnswerId, speechAnswerId)
				&& Objects.equals(this.processingToken, processingToken);
		}

		private int subscriberCount() {
			return subscribers.get();
		}

		private SpeechAnswerTask sharedTask() {
			return sharedTask;
		}

		private synchronized SpeechAnswerTask subscribe() {
			CompletableFuture<SpeechAnswerResult> subscriberResult =
				new CompletableFuture<>();
			AtomicBoolean subscribed = new AtomicBoolean(true);
			subscribers.incrementAndGet();
			sharedTask.result().whenComplete((result, exception) -> {
				if (subscribed.compareAndSet(true, false)) {
					subscribers.decrementAndGet();
				}
				if (exception == null) {
					subscriberResult.complete(result);
				} else {
					subscriberResult.completeExceptionally(exception);
				}
			});
			return new SpeechAnswerTask(
				subscriberResult,
				() -> unsubscribe(subscribed)
			);
		}

		private synchronized boolean unsubscribe(AtomicBoolean subscribed) {
			if (!subscribed.compareAndSet(true, false)) {
				return false;
			}
			if (subscribers.decrementAndGet() == 0) {
				sharedTask.cancel();
			}
			return true;
		}

		private void expire() {
			sharedTask.cancel();
		}
	}
}
