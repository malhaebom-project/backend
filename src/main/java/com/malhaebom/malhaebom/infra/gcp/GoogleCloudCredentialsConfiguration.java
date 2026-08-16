package com.malhaebom.malhaebom.infra.gcp;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.GoogleCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GcpCredentialsProperties.class)
public class GoogleCloudCredentialsConfiguration {

	private static final List<String> CLOUD_PLATFORM_SCOPE = List.of(
		"https://www.googleapis.com/auth/cloud-platform"
	);

	@Bean
	CredentialsProvider googleCloudCredentialsProvider(
		GcpCredentialsProperties properties
	) throws IOException {
		if (properties.credentials() == null) {
			return GoogleCredentialsProvider.newBuilder()
				.setScopesToApply(CLOUD_PLATFORM_SCOPE)
				.build();
		}

		try (InputStream input = properties.credentials().getInputStream()) {
			GoogleCredentials credentials = GoogleCredentials.fromStream(input)
				.createScoped(CLOUD_PLATFORM_SCOPE);
			return FixedCredentialsProvider.create(credentials);
		}
	}
}
