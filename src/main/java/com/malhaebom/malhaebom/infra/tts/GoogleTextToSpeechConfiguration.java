package com.malhaebom.malhaebom.infra.tts;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(GoogleTextToSpeechProperties.class)
@ConditionalOnProperty(
	prefix = "gcp.tts",
	name = "enabled",
	havingValue = "true"
)
public class GoogleTextToSpeechConfiguration {
	@Bean(destroyMethod = "close")
	public TextToSpeechClient googleCloudTextToSpeechClient(CredentialsProvider credentialsProvider) throws IOException {
		TextToSpeechSettings settings =
			TextToSpeechSettings.newBuilder()
				.setCredentialsProvider(credentialsProvider)
				.build();

		return TextToSpeechClient.create(settings);
	}
}
