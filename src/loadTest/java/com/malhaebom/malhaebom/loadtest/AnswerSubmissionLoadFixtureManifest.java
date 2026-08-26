package com.malhaebom.malhaebom.loadtest;

import java.util.List;

public record AnswerSubmissionLoadFixtureManifest(
	String runId,
	String accessToken,
	Long questionId,
	List<StageFixtures> stages
) {

	public AnswerSubmissionLoadFixtureManifest {
		stages = List.copyOf(stages);
	}

	public List<Fixture> allFixtures() {
		return stages.stream()
			.flatMap(stage -> stage.fixtures().stream())
			.toList();
	}

	public record StageFixtures(
		int concurrency,
		List<Fixture> fixtures
	) {

		public StageFixtures {
			fixtures = List.copyOf(fixtures);
		}
	}

	public record Fixture(
		Long sessionId,
		Long sessionQuestionId,
		Long speechAnswerId
	) {
	}
}
