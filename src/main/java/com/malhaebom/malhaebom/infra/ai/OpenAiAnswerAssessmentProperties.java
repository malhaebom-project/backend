package com.malhaebom.malhaebom.infra.ai;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "spring.ai.openai")
public class OpenAiAnswerAssessmentProperties {

	private String baseUrl;
	private String apiKey;
	private String organizationId;
	private Duration timeout = Duration.ofSeconds(60);
	private int maxRetries = 3;
	private boolean connectionPoolMetricsEnabled;
	private Map<String, String> customHeaders = new HashMap<>();
	private Chat chat = new Chat();

	@Getter
	@Setter
	public static class Chat {

		private String model = OpenAiChatOptions.DEFAULT_CHAT_MODEL;
		private String reasoningEffort;
		private String verbosity;
		private Long maxCompletionTokens;
	}
}
