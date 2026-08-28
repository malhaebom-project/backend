package com.malhaebom.malhaebom.infra.observability;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.global.concurrent.CompletionFailures;

@Component
public class MicrometerOpenAiAnswerAssessmentMetricsRecorder
	implements OpenAiAnswerAssessmentMetricsRecorder {

	private static final String TOKEN_METRIC = "malhaebom.openai.answer.assessment.tokens";
	private static final String FAILURE_METRIC = "malhaebom.openai.answer.assessment.failures";

	private final Map<TokenType, Counter> tokenCounters = new EnumMap<>(TokenType.class);
	private final Map<FailureReason, Counter> failureCounters = new EnumMap<>(FailureReason.class);

	public MicrometerOpenAiAnswerAssessmentMetricsRecorder(
		MeterRegistry meterRegistry
	) {
		Objects.requireNonNull(
			meterRegistry,
			"MeterRegistry는 null일 수 없습니다."
		);
		for (TokenType type : TokenType.values()) {
			tokenCounters.put(
				type,
				Counter.builder(TOKEN_METRIC)
					.tag("type", type.tagValue)
					.register(meterRegistry)
			);
		}
		for (FailureReason reason : FailureReason.values()) {
			failureCounters.put(
				reason,
				Counter.builder(FAILURE_METRIC)
					.tag("reason", reason.tagValue())
					.register(meterRegistry)
			);
		}
	}

	@Override
	public void recordTokenUsage(
		long promptTokens,
		long completionTokens,
		long totalTokens,
		long cachedTokens,
		long reasoningTokens
	) {
		increment(TokenType.PROMPT, promptTokens);
		increment(TokenType.COMPLETION, completionTokens);
		increment(TokenType.TOTAL, totalTokens);
		increment(TokenType.CACHED, cachedTokens);
		increment(TokenType.REASONING, reasoningTokens);
	}

	@Override
	public void recordFailure(FailureReason reason) {
		failureCounters.get(Objects.requireNonNull(reason)).increment();
	}

	@Override
	public void recordFailure(Throwable failure) {
		recordFailure(classifyFailure(Objects.requireNonNull(failure)));
	}

	private void increment(TokenType type, long tokens) {
		if (tokens > 0) {
			tokenCounters.get(type).increment(tokens);
		}
	}

	private FailureReason classifyFailure(Throwable failure) {
		Throwable cause = CompletionFailures.unwrap(failure);
		if (cause instanceof CancellationException) {
			return FailureReason.CANCELLED;
		}
		if (cause instanceof OpenAIServiceException serviceException) {
			int status = serviceException.statusCode();
			if (status == 429) {
				return FailureReason.RATE_LIMIT;
			}
			if (status == 401) {
				return FailureReason.AUTHENTICATION;
			}
			if (status == 403) {
				return FailureReason.PERMISSION;
			}
			if (status == 408 || status == 504) {
				return FailureReason.TIMEOUT;
			}
			if (status >= 400 && status < 500) {
				return FailureReason.BAD_REQUEST;
			}
			if (status >= 500) {
				return FailureReason.SERVER_ERROR;
			}
		}
		if (cause instanceof OpenAIIoException ioException) {
			return isTimeoutCause(ioException.getCause())
				? FailureReason.TIMEOUT
				: FailureReason.IO_ERROR;
		}
		return FailureReason.UNKNOWN;
	}

	private boolean isTimeoutCause(Throwable failure) {
		return failure instanceof SocketTimeoutException
			|| failure instanceof HttpTimeoutException;
	}

	private enum TokenType {
		PROMPT("prompt"),
		COMPLETION("completion"),
		TOTAL("total"),
		CACHED("cached"),
		REASONING("reasoning");

		private final String tagValue;

		TokenType(String tagValue) {
			this.tagValue = tagValue;
		}
	}
}
