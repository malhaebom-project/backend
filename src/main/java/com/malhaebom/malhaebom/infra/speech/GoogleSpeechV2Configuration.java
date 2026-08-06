package com.malhaebom.malhaebom.infra.speech;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
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
		GoogleCredentials credentials = loadCredentials(properties);
		SpeechSettings.Builder settings = SpeechSettings.newBuilder()
			.setCredentialsProvider(
				FixedCredentialsProvider.create(credentials)
			);
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

	private GoogleCredentials loadCredentials(
		GoogleSpeechV2Properties properties
	) throws IOException {
		try (InputStream input = properties.google()
			.credentials()
			.location()
			.getInputStream()) {
			return ServiceAccountCredentials.fromStream(input)
				.createScoped(SpeechSettings.getDefaultServiceScopes());
		}
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
