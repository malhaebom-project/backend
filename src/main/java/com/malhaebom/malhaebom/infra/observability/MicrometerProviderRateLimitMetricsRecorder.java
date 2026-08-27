package com.malhaebom.malhaebom.infra.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public class MicrometerProviderRateLimitMetricsRecorder
	implements ProviderRateLimitMetricsRecorder {

	private static final String REQUEST_METRIC =
		"malhaebom.ai.provider.rate.limit.requests";
	private static final String AVAILABLE_METRIC =
		"malhaebom.ai.provider.rate.limit.available";
	private static final String CAPACITY_METRIC =
		"malhaebom.ai.provider.rate.limit.capacity";

	private final MeterRegistry meterRegistry;
	private final List<LongSupplier> availableTokenSuppliers =
		new ArrayList<>();
	private final List<AtomicLong> capacities = new ArrayList<>();

	public MicrometerProviderRateLimitMetricsRecorder(
		MeterRegistry meterRegistry
	) {
		this.meterRegistry = Objects.requireNonNull(
			meterRegistry,
			"MeterRegistry는 null일 수 없습니다."
		);
	}

	@Override
	public synchronized void bindCapacity(
		String provider,
		String quota,
		long capacity
	) {
		AtomicLong value = new AtomicLong(capacity);
		capacities.add(value);
		Gauge.builder(CAPACITY_METRIC, value, AtomicLong::get)
			.tag("provider", Objects.requireNonNull(provider))
			.tag("quota", Objects.requireNonNull(quota))
			.register(meterRegistry);
	}

	@Override
	public synchronized void bindAvailable(
		String provider,
		String quota,
		LongSupplier availableTokens
	) {
		LongSupplier supplier = Objects.requireNonNull(availableTokens);
		availableTokenSuppliers.add(supplier);
		Gauge.builder(
			AVAILABLE_METRIC,
			supplier,
			LongSupplier::getAsLong
		).tag("provider", Objects.requireNonNull(provider))
			.tag("quota", Objects.requireNonNull(quota))
			.register(meterRegistry);
	}

	@Override
	public void record(String provider, Result result) {
		meterRegistry.counter(
			REQUEST_METRIC,
			"provider",
			Objects.requireNonNull(provider),
			"result",
			Objects.requireNonNull(result).tagValue()
		).increment();
	}
}
