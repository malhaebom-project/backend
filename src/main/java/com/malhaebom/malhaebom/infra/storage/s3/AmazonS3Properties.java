package com.malhaebom.malhaebom.infra.storage.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws.s3")
public record AmazonS3Properties(
	String accessKey,
	String secretKey,
	String region,
	String bucket,
	String baseUrl,
	String keyPrefix
) {
}
