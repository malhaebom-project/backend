package com.malhaebom.malhaebom.infra.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcp.tts")
public record GoogleTextToSpeechProperties(String languageCode, String voiceName, double speakingRate, double pitch) {}
