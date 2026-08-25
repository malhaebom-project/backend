package com.malhaebom.malhaebom.infra.ai;

import java.util.List;

import com.openai.client.OpenAIClientAsync;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
	OpenAiAnswerAssessmentProperties.class,
	AnswerAssessmentQueueProperties.class,
	OpenAiAnswerAssessmentRateLimitProperties.class
})
public class OpenAiAnswerAssessmentConfiguration {

	@Bean(destroyMethod = "close")
	ExecutorAnswerAssessmentQueueTimeoutScheduler
	answerAssessmentQueueTimeoutScheduler() {
		return new ExecutorAnswerAssessmentQueueTimeoutScheduler();
	}

	@Bean(destroyMethod = "close")
	OpenAIClientAsync answerAssessmentOpenAiClient(
		OpenAiAnswerAssessmentProperties properties,
		ObjectProvider<ObservationRegistry> observationRegistries,
		ObjectProvider<MeterRegistry> meterRegistries,
		ObjectProvider<OpenAiHttpClientBuilderCustomizer> customizers
	) {
		MeterRegistry meterRegistry = properties
			.isConnectionPoolMetricsEnabled()
			? meterRegistries.getIfAvailable()
			: null;
		List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers =
			customizers.orderedStream().toList();

		return OpenAiSetup.setupAsyncClient(
			properties.getBaseUrl(),
			properties.getApiKey(),
			null,
			null,
			null,
			properties.getOrganizationId(),
			false,
			false,
			properties.getChat().getModel(),
			properties.getTimeout(),
			properties.getMaxRetries(),
			null,
			properties.getCustomHeaders(),
			observationRegistries.getIfUnique(
				() -> ObservationRegistry.NOOP
			),
			meterRegistry,
			httpClientCustomizers
		);
	}
}
