package com.malhaebom.malhaebom.infra.speech;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoogleSpeechRateLimitProperties.class)
class SpeechRateLimitConfiguration {
}
