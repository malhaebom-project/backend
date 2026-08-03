package com.malhaebom.malhaebom.infra.tts;

import java.io.IOException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.TextToSpeechSettings;

@Configuration
@EnableConfigurationProperties(GoogleTextToSpeechProperties.class)
@ConditionalOnProperty(
	prefix = "malhaebom.tts",
	name = "enabled",
	havingValue = "true"
)
public class GoogleTextToSpeechConfiguration {

	@Bean(destroyMethod = "close")
	public TextToSpeechClient googleCloudTextToSpeechClient(
		GoogleTextToSpeechProperties properties
	) throws IOException {
		GoogleTextToSpeechProperties.Credentials credentialProperties =
			properties.credentials();
		ServiceAccountCredentials credentials =
			ServiceAccountCredentials.newBuilder()
				.setProjectId(credentialProperties.projectId())
				.setClientEmail(credentialProperties.clientEmail())
				.setPrivateKeyId(credentialProperties.privateKeyId())
				.setPrivateKeyString(credentialProperties.privateKey())
				.setScopes(TextToSpeechSettings.getDefaultServiceScopes())
				.build();
		TextToSpeechSettings settings =
			TextToSpeechSettings.newBuilder()
				.setCredentialsProvider(
					FixedCredentialsProvider.create(credentials)
				)
				.build();

		return TextToSpeechClient.create(settings);
	}
}
