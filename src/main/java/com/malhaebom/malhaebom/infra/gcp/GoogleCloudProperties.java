package com.malhaebom.malhaebom.infra.gcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "google.cloud")
public record GoogleCloudProperties(
	@NotBlank String projectId,
	Credentials credentials
) {

	public record Credentials(
		Resource location
	) {
	}
}
