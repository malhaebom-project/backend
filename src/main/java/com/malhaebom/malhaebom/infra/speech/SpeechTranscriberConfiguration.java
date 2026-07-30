package com.malhaebom.malhaebom.infra.speech;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.malhaebom.malhaebom.service.speech.SpeechTranscriber;

@Configuration(proxyBeanMethods = false)
public class SpeechTranscriberConfiguration {

	@Bean
	@ConditionalOnMissingBean(SpeechTranscriber.class)
	SpeechTranscriber unavailableSpeechTranscriber() {
		return (speechAnswerId, requestKey, audio) -> {
			throw new IllegalStateException(
				"SpeechTranscriber 구현체가 구성되지 않았습니다."
			);
		};
	}
}
