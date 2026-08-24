package com.malhaebom.malhaebom.infra.observability;

public interface OpenAiAnswerAssessmentMetricsRecorder {

	void recordTokenUsage(
		long promptTokens,
		long completionTokens,
		long totalTokens,
		long cachedTokens,
		long reasoningTokens
	);

	void recordFailure(FailureReason reason);

	void recordFailure(Throwable failure);

	enum FailureReason {
		RATE_LIMIT("rate_limit"),
		TIMEOUT("timeout"),
		AUTHENTICATION("authentication"),
		PERMISSION("permission"),
		BAD_REQUEST("bad_request"),
		SERVER_ERROR("server_error"),
		IO_ERROR("io_error"),
		CANCELLED("cancelled"),
		REFUSAL("refusal"),
		EMPTY_RESPONSE("empty_response"),
		INVALID_RESPONSE("invalid_response"),
		UNKNOWN("unknown");

		private final String tagValue;

		FailureReason(String tagValue) {
			this.tagValue = tagValue;
		}

		public String tagValue() {
			return tagValue;
		}
	}
}
