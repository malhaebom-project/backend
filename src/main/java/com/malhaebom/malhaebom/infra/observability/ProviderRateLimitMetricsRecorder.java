package com.malhaebom.malhaebom.infra.observability;

import java.util.function.LongSupplier;

public interface ProviderRateLimitMetricsRecorder {

	ProviderRateLimitMetricsRecorder NOOP = new ProviderRateLimitMetricsRecorder() {
		@Override
		public void bindCapacity(String provider, String quota, long capacity) {
		}

		@Override
		public void bindAvailable(
			String provider,
			String quota,
			LongSupplier availableTokens
		) {
		}

		@Override
		public void record(String provider, Result result) {
		}
	};

	void bindCapacity(String provider, String quota, long capacity);

	void bindAvailable(
		String provider,
		String quota,
		LongSupplier availableTokens
	);

	void record(String provider, Result result);

	enum Result {
		ALLOWED("allowed"),
		DELAYED("delayed"),
		REJECTED("rejected");

		private final String tagValue;

		Result(String tagValue) {
			this.tagValue = tagValue;
		}

		public String tagValue() {
			return tagValue;
		}
	}
}
