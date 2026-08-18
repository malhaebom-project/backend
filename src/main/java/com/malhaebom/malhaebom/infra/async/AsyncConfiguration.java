package com.malhaebom.malhaebom.infra.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@EnableConfigurationProperties(AnswerSubmissionAsyncProperties.class)
public class AsyncConfiguration {
}
