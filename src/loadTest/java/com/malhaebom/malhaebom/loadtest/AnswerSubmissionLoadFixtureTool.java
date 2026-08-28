package com.malhaebom.malhaebom.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.malhaebom.malhaebom.MalhaebomApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class AnswerSubmissionLoadFixtureTool {
	private static final String ACTION_PROPERTY = "load.test.action";
	private static final String RUN_ID_PROPERTY = "load.test.run-id";
	private static final String STAGES_PROPERTY = "load.test.stages";
	private static final String MANIFEST_PROPERTY = "load.test.manifest";

	private AnswerSubmissionLoadFixtureTool() {}

	public static void main(String[] args) throws Exception {
		configureNonWebDependencies();
		try (ConfigurableApplicationContext context =
			new SpringApplicationBuilder(MalhaebomApplication.class)
				.web(WebApplicationType.NONE)
				.run(args)) {
			AnswerSubmissionLoadFixtureService fixtures = context.getBean(
				AnswerSubmissionLoadFixtureService.class
			);
			ObjectMapper mapper = new ObjectMapper()
				.findAndRegisterModules()
				.enable(SerializationFeature.INDENT_OUTPUT);
			Path manifestPath = Path.of(requiredProperty(MANIFEST_PROPERTY))
				.toAbsolutePath()
				.normalize();
			String action = requiredProperty(ACTION_PROPERTY);
			switch (action) {
				case "seed" -> seed(fixtures, mapper, manifestPath);
				case "cleanup" -> cleanup(fixtures, mapper, manifestPath);
				default -> throw new IllegalArgumentException(
					"loadTestAction은 seed 또는 cleanup이어야 합니다."
				);
			}
		}
	}

	private static void seed(
		AnswerSubmissionLoadFixtureService fixtures,
		ObjectMapper mapper,
		Path manifestPath
	) throws Exception {
		String runId = System.getProperty(RUN_ID_PROPERTY);
		if (runId == null || runId.isBlank()) {
			runId = UUID.randomUUID().toString();
		}
		AnswerSubmissionLoadFixtureManifest manifest = fixtures.seed(
			runId,
			stages(requiredProperty(STAGES_PROPERTY))
		);
		Path parent = manifestPath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		mapper.writeValue(manifestPath.toFile(), manifest);
		System.out.printf(
			"LOAD_TEST_FIXTURES_SEEDED run_id=%s count=%d manifest=%s%n",
			manifest.runId(),
			manifest.allFixtures().size(),
			manifestPath
		);
	}

	private static void cleanup(
		AnswerSubmissionLoadFixtureService fixtures,
		ObjectMapper mapper,
		Path manifestPath
	) throws Exception {
		AnswerSubmissionLoadFixtureManifest manifest = mapper.readValue(
			manifestPath.toFile(),
			AnswerSubmissionLoadFixtureManifest.class
		);
		fixtures.cleanup(manifest);
		System.out.printf(
			"LOAD_TEST_FIXTURES_CLEANED run_id=%s count=%d%n",
			manifest.runId(),
			manifest.allFixtures().size()
		);
	}

	private static List<Integer> stages(String value) {
		return Arrays.stream(value.split(","))
			.map(String::strip)
			.map(Integer::valueOf)
			.toList();
	}

	private static String requiredProperty(String name) {
		String value = System.getProperty(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
				"필수 시스템 속성이 없습니다: " + name
			);
		}
		return value;
	}

	private static void configureNonWebDependencies() {
		System.setProperty("gcp.stt.enabled", "false");
		System.setProperty("gcp.tts.enabled", "false");
		System.setProperty("malhaebom.tts.enabled", "false");
		System.setProperty(
			"spring.ai.openai.api-key",
			"load-test-fixture-not-used"
		);
	}
}
