package com.malhaebom.malhaebom.service.port;

public interface AnswerSubmissionMetricsRecorder {

	void recordNew();

	void recordCached();

	void recordProcessing();

	void recordRetry();

	void recordReclaimed();
}
