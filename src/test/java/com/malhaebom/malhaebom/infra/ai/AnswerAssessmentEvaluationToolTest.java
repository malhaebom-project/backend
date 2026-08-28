package com.malhaebom.malhaebom.infra.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;

class AnswerAssessmentEvaluationToolTest {
	@Test
	void 사람_검토가_끝나지_않은_데이터셋은_기본적으로_실행하지_않는다() {
		var dataset = new AnswerAssessmentEvaluationTool.Dataset(
			"test", "HUMAN_REVIEW_REQUIRED", List.of(question(
				"case-1", Difficulty.EASY, AnswerResult.CORRECT, false
			))
		);

		assertThrows(
			IllegalStateException.class,
			() -> AnswerAssessmentEvaluationTool.flattenAndValidate(
				dataset, settings(false)
			)
		);
	}

	@Test
	void 모든_반복과_정량_기준을_만족하면_최종_통과한다() {
		Fixture fixture = fixture();
		List<AnswerAssessmentEvaluationTool.Observation> observations =
			perfectObservations(fixture.cases());

		var summary = AnswerAssessmentEvaluationTool.summarize(
			fixture.dataset(), settings(true), fixture.cases(), observations
		);

		assertTrue(summary.passed());
		assertTrue(summary.gates().stream().allMatch(
			AnswerAssessmentEvaluationTool.Gate::passed
		));
	}

	@Test
	void 중요_오답을_한_번이라도_정답으로_판정하면_최종_실패한다() {
		Fixture fixture = fixture();
		List<AnswerAssessmentEvaluationTool.Observation> observations =
			new ArrayList<>(perfectObservations(fixture.cases()));
		var critical = fixture.cases().stream()
			.filter(item -> item.candidate().criticalFalsePositive())
			.findFirst().orElseThrow();
		observations.set(
			observations.indexOf(observations.stream()
				.filter(item -> item.caseId().equals(critical.caseId()))
				.findFirst().orElseThrow()),
			AnswerAssessmentEvaluationTool.Observation.success(
				1, critical, new AnswerAssessment(true, 50, 30, 20, "ok"), 1
			)
		);

		var summary = AnswerAssessmentEvaluationTool.summarize(
			fixture.dataset(), settings(true), fixture.cases(), observations
		);

		assertFalse(summary.passed());
		assertFalse(summary.gates().stream()
			.filter(gate -> gate.name().equals("critical_incorrect_to_correct"))
			.findFirst().orElseThrow().passed());
	}

	private static Fixture fixture() {
		List<AnswerAssessmentEvaluationTool.Question> questions = List.of(
			question("easy-correct", Difficulty.EASY, AnswerResult.CORRECT, false),
			question("normal-partial", Difficulty.NORMAL, AnswerResult.PARTIALLY_CORRECT, false),
			question("hard-incorrect", Difficulty.HARD, AnswerResult.INCORRECT, true)
		);
		var dataset = new AnswerAssessmentEvaluationTool.Dataset(
			"test", "HUMAN_APPROVED", questions
		);
		return new Fixture(
			dataset,
			AnswerAssessmentEvaluationTool.flattenAndValidate(dataset, settings(false))
		);
	}

	private static List<AnswerAssessmentEvaluationTool.Observation> perfectObservations(
		List<AnswerAssessmentEvaluationTool.EvalCase> cases
	) {
		List<AnswerAssessmentEvaluationTool.Observation> observations = new ArrayList<>();
		for (int round = 1; round <= 10; round++) {
			for (var evalCase : cases) {
				AnswerAssessment assessment = switch (evalCase.expectedResult()) {
					case CORRECT -> new AnswerAssessment(true, 50, 30, 20, "ok");
					case PARTIALLY_CORRECT -> new AnswerAssessment(true, 35, 10, 5, "retry");
					case INCORRECT -> new AnswerAssessment(true, 10, 10, 10, "retry");
					case UNRECOGNIZED -> throw new AssertionError();
				};
				observations.add(AnswerAssessmentEvaluationTool.Observation.success(
					round, evalCase, assessment, 1
				));
			}
		}
		return observations;
	}

	private static AnswerAssessmentEvaluationTool.Question question(
		String caseId,
		Difficulty difficulty,
		AnswerResult expected,
		boolean critical
	) {
		return new AnswerAssessmentEvaluationTool.Question(
			1, difficulty, QuestionType.SHORT_ANSWER,
			"Question?", "질문?", "", "Model answer.", List.of("Model answer."),
			List.of(new AnswerAssessmentEvaluationTool.Case(
				caseId, "Student answer.", expected, critical
			))
		);
	}

	private static AnswerAssessmentEvaluationTool.Settings settings(boolean allowUnreviewed) {
		return new AnswerAssessmentEvaluationTool.Settings(
			Path.of("dataset.json"), Path.of("reports"), 10, 1, 90,
			"test-model", allowUnreviewed
		);
	}

	private record Fixture(
		AnswerAssessmentEvaluationTool.Dataset dataset,
		List<AnswerAssessmentEvaluationTool.EvalCase> cases
	) {}
}
