package com.malhaebom.malhaebom.infra.async;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties({
	AnswerSubmissionAsyncProperties.class,
	SpeechAnswerAsyncProperties.class
})
public class AsyncConfiguration {

	public static final String SPEECH_COMPLETION_EXECUTOR =
		"speechCompletionExecutor";

	@Bean(name = SPEECH_COMPLETION_EXECUTOR)
	ThreadPoolTaskExecutor speechCompletionExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(8);
		executor.setThreadNamePrefix("speech-completion-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(20);
		return executor;
	}
}
