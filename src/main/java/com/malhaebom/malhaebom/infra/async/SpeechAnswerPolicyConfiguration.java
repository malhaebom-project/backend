package com.malhaebom.malhaebom.infra.async;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.malhaebom.malhaebom.presentation.config.SpeechRequestTimeout;
import com.malhaebom.malhaebom.service.policy.SpeechProcessingLease;
import com.malhaebom.malhaebom.service.policy.SpeechShutdownPolicy;
import com.malhaebom.malhaebom.service.policy.SpeechTranscriptionConcurrencyPolicy;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SpeechAnswerAsyncProperties.class)
public class SpeechAnswerPolicyConfiguration {

	@Bean
	SpeechRequestTimeout speechRequestTimeout(
		SpeechAnswerAsyncProperties properties
	) {
		return new SpeechRequestTimeout(properties.requestTimeout());
	}

	@Bean
	SpeechProcessingLease speechProcessingLease(
		SpeechAnswerAsyncProperties properties
	) {
		return new SpeechProcessingLease(properties.processingLease());
	}

	@Bean
	SpeechShutdownPolicy speechShutdownPolicy(
		SpeechAnswerAsyncProperties properties
	) {
		return new SpeechShutdownPolicy(
			properties.shutdownDrainTimeout()
		);
	}

	@Bean
	SpeechTranscriptionConcurrencyPolicy speechTranscriptionConcurrencyPolicy(
		SpeechAnswerAsyncProperties properties
	) {
		return new SpeechTranscriptionConcurrencyPolicy(
			properties.maxConcurrentRequests()
		);
	}
}
