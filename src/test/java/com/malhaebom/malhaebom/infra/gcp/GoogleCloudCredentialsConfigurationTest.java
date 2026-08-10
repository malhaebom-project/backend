package com.malhaebom.malhaebom.infra.gcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.GoogleCredentialsProvider;
import com.google.auth.oauth2.UserCredentials;

class GoogleCloudCredentialsConfigurationTest {

	private final GoogleCloudCredentialsConfiguration configuration =
		new GoogleCloudCredentialsConfiguration();

	@Test
	void 자격증명_파일_경로가_없으면_ADC를_사용한다() throws Exception {
		CredentialsProvider provider = configuration
			.googleCloudCredentialsProvider(
				new GoogleCloudProperties(null, null)
			);

		assertThat(provider).isInstanceOf(GoogleCredentialsProvider.class);
	}

	@Test
	void 자격증명_파일_경로가_있으면_해당_파일을_사용한다() throws Exception {
		String credentialsJson = """
			{
			  "type": "authorized_user",
			  "client_id": "test-client-id",
			  "client_secret": "test-client-secret",
			  "refresh_token": "test-refresh-token"
			}
			""";
		ByteArrayResource resource = new ByteArrayResource(
			credentialsJson.getBytes(StandardCharsets.UTF_8)
		);

		CredentialsProvider provider = configuration
			.googleCloudCredentialsProvider(
				new GoogleCloudProperties(
					null,
					new GoogleCloudProperties.Credentials(resource)
				)
			);

		assertThat(provider).isInstanceOf(FixedCredentialsProvider.class);
		assertThat(provider.getCredentials())
			.isInstanceOf(UserCredentials.class);
	}
}
