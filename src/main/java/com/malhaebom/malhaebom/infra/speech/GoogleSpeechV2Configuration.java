package com.malhaebom.malhaebom.infra.speech;

import java.io.IOException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechSettings;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@Configuration(proxyBeanMethods = false)
@Profile("!local-fake-stt")
@EnableConfigurationProperties(GoogleSpeechV2Properties.class)
public class GoogleSpeechV2Configuration {

	private static final String GLOBAL_LOCATION = "global";
	private static final int GOOGLE_API_PORT = 443;

	@Bean(destroyMethod = "close")
	SpeechClient googleSpeechV2Client(
		GoogleSpeechV2Properties properties
	) throws IOException {
		SpeechSettings.Builder settings = SpeechSettings.newBuilder();
		settings.recognizeSettings()
			.setSimpleTimeoutNoRetriesDuration(properties.timeout());

		String location = properties.google().location();
		if (!GLOBAL_LOCATION.equals(location)) {
			settings.setEndpoint(
				location + "-speech.googleapis.com:" + GOOGLE_API_PORT
			);
		}

		return SpeechClient.create(settings.build());
	}

	@Bean
	@Primary
	SpeechTranscriber googleSpeechV2Transcriber(
		SpeechClient client,
		GoogleSpeechV2Properties properties
	) {
		return new GoogleSpeechV2Transcriber(client, properties);
	}
}
