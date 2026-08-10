package com.malhaebom.malhaebom.infra.speech;

import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.speech.v2.SpeechClient;
import com.google.cloud.speech.v2.SpeechSettings;
import com.malhaebom.malhaebom.infra.gcp.GoogleCloudProperties;
import com.malhaebom.malhaebom.service.port.SpeechTranscriber;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
	prefix = "google.stt",
	name = "enabled",
	havingValue = "true"
)
@EnableConfigurationProperties(GoogleSpeechV2Properties.class)
public class GoogleSpeechV2Configuration {

	private static final String GLOBAL_LOCATION = "global";
	private static final int GOOGLE_API_PORT = 443;

	@Bean(destroyMethod = "close")
	SpeechClient googleSpeechV2Client(
		GoogleSpeechV2Properties properties,
		CredentialsProvider credentialsProvider
	) throws IOException {
		SpeechSettings.Builder settings = SpeechSettings.newBuilder()
			.setCredentialsProvider(credentialsProvider);
		settings.recognizeSettings()
			.setSimpleTimeoutNoRetriesDuration(properties.timeout());

		String location = properties.location();
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
		GoogleSpeechV2Properties properties,
		GoogleCloudProperties cloudProperties
	) {
		return new GoogleSpeechV2Transcriber(
			client,
			properties,
			cloudProperties
		);
	}
}
