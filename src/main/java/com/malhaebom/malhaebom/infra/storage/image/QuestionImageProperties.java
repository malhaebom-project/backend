package com.malhaebom.malhaebom.infra.storage.image;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "malhaebom.assets")
public record QuestionImageProperties(
	String baseUrl
) {
}
