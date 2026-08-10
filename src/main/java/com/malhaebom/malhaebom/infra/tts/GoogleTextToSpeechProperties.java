package com.malhaebom.malhaebom.infra.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.tts")
public record GoogleTextToSpeechProperties(
	String languageCode,
	String voiceName,
	double speakingRate,
	double pitch
) {
}
