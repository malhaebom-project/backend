package com.malhaebom.malhaebom.infra.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

import com.malhaebom.malhaebom.service.port.AnswerSubmissionMetricsRecorder;

@Component
public class MicrometerAnswerSubmissionMetricsRecorder
	implements AnswerSubmissionMetricsRecorder {

	private static final String PREPARE_METRIC =
		"malhaebom.answer.submission.prepare";

	private final Counter newSubmissions;
	private final Counter cachedSubmissions;
	private final Counter processingSubmissions;
	private final Counter retriedSubmissions;
	private final Counter reclaimedSubmissions;

	public MicrometerAnswerSubmissionMetricsRecorder(
		MeterRegistry meterRegistry
	) {
		newSubmissions = prepareCounter(meterRegistry, "new");
		cachedSubmissions = prepareCounter(meterRegistry, "cached");
		processingSubmissions = prepareCounter(meterRegistry, "processing");
		retriedSubmissions = prepareCounter(meterRegistry, "retry");
		reclaimedSubmissions = prepareCounter(meterRegistry, "reclaimed");
	}

	@Override
	public void recordNew() {
		newSubmissions.increment();
	}

	@Override
	public void recordCached() {
		cachedSubmissions.increment();
	}

	@Override
	public void recordProcessing() {
		processingSubmissions.increment();
	}

	@Override
	public void recordRetry() {
		retriedSubmissions.increment();
	}

	@Override
	public void recordReclaimed() {
		reclaimedSubmissions.increment();
	}

	private Counter prepareCounter(MeterRegistry meterRegistry, String result) {
		return Counter.builder(PREPARE_METRIC)
			.description("Answer submission preparation outcomes")
			.tag("result", result)
			.register(meterRegistry);
	}
}
