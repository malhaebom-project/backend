package com.malhaebom.malhaebom.service.port;

public interface SpeechAnswerLifecycleOperations {

	void start();

	void stop();

	void stop(Runnable callback);

	boolean isRunning();
}
