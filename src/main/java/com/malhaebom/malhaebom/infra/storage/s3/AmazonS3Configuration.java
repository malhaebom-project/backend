package com.malhaebom.malhaebom.infra.storage.s3;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(AmazonS3Properties.class)
@ConditionalOnProperty(
	prefix = "malhaebom.tts",
	name = "enabled",
	havingValue = "true"
)
public class AmazonS3Configuration {
	@Bean(destroyMethod = "close")
	public S3Client amazonS3Client(AmazonS3Properties properties) {
		AwsBasicCredentials credentials = AwsBasicCredentials.create(
			properties.accessKey(),
			properties.secretKey()
		);

		return S3Client.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(
				StaticCredentialsProvider.create(credentials)
			)
			.build();
	}
}
