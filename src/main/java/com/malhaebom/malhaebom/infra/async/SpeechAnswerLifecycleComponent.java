package com.malhaebom.malhaebom.infra.async;

import com.malhaebom.malhaebom.service.port.SpeechAnswerLifecycleOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpeechAnswerLifecycleComponent implements SmartLifecycle {
	private final SpeechAnswerLifecycleOperations lifecycle;

	@Override
	public void start() {
		lifecycle.start();
	}

	@Override
	public void stop() {
		lifecycle.stop();
	}

	@Override
	public void stop(Runnable callback) {
		lifecycle.stop(callback);
	}

	@Override
	public boolean isRunning() {
		return lifecycle.isRunning();
	}

	@Override
	public int getPhase() {
		return Integer.MAX_VALUE;
	}
}
