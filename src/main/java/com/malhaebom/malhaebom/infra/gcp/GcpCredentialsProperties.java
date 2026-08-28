package com.malhaebom.malhaebom.infra.gcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "gcp")
public record GcpCredentialsProperties(Resource credentials) { }
