package com.malhaebom.malhaebom.infra.ai;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.github.bucket4j.TimeMeter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openai.client.OpenAIClientAsync;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;

import com.malhaebom.malhaebom.domain.learning.AnswerResult;
import com.malhaebom.malhaebom.domain.learning.Difficulty;
import com.malhaebom.malhaebom.domain.learning.QuestionType;
import com.malhaebom.malhaebom.infra.observability.AnswerAssessmentMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.OpenAiAnswerAssessmentMetricsRecorder;
import com.malhaebom.malhaebom.infra.observability.ProviderRateLimitMetricsRecorder;
import com.malhaebom.malhaebom.service.dto.AnswerAssessment;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentInput;
import com.malhaebom.malhaebom.service.dto.AnswerAssessmentTask;

public final class AnswerAssessmentEvaluationTool {

	private static final ObjectMapper JSON = new ObjectMapper()
		.enable(SerializationFeature.INDENT_OUTPUT);
	private static final DateTimeFormatter RUN_ID = DateTimeFormatter
		.ofPattern("uuuuMMdd-HHmmss", Locale.ROOT)
		.withZone(ZoneOffset.UTC);
	private static final double OVERALL_ACCURACY_THRESHOLD = 0.90;
	private static final double VALID_RESPONSE_THRESHOLD = 0.99;
	private static final double MACRO_F1_THRESHOLD = 0.85;

	private AnswerAssessmentEvaluationTool() {}

	public static void main(String[] args) throws Exception {
		Settings settings = Settings.fromSystemProperties();
		Dataset dataset = JSON.readValue(settings.dataset().toFile(), Dataset.class);
		List<EvalCase> cases = flattenAndValidate(dataset, settings);
		Path runDirectory = settings.outputDirectory().resolve(RUN_ID.format(Instant.now()));
		Files.createDirectories(runDirectory);
		Path rawResults = runDirectory.resolve("results.jsonl");

		List<Observation> observations;
		OpenAIClientAsync client = createClient(settings);
		GeneratorHarness harness = createGenerator(client, settings);
		try {
			observations = execute(harness.generator(), harness.tokenUsage(), cases, settings, rawResults);
		} finally {
			harness.queue().shutdown();
			harness.timeoutScheduler().close();
			client.close();
		}

		Summary summary = summarize(
			dataset, settings, cases, observations, harness.tokenUsage().snapshot()
		);
		JSON.writeValue(runDirectory.resolve("summary.json").toFile(), summary);
		Files.writeString(
			runDirectory.resolve("report.md"),
			markdown(summary),
			StandardCharsets.UTF_8
		);
		System.out.printf("Evaluation %s. Report: %s%n",
			summary.passed() ? "PASSED" : "FAILED",
			runDirectory.resolve("report.md").toAbsolutePath());
		if (!summary.passed()) {
			System.exit(2);
		}
	}

	private static OpenAIClientAsync createClient(Settings settings) {
		String apiKey = System.getenv("OPENAI_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("OPENAI_API_KEY is required.");
		}
		OpenAiAnswerAssessmentProperties properties = new OpenAiAnswerAssessmentProperties();
		properties.setApiKey(apiKey);
		properties.setTimeout(Duration.ofSeconds(90));
		properties.setMaxRetries(1);
		configureChat(properties, settings.model());
		StaticListableBeanFactory beans = new StaticListableBeanFactory();
		return new OpenAiAnswerAssessmentConfiguration().answerAssessmentOpenAiClient(
			properties,
			beans.getBeanProvider(io.micrometer.observation.ObservationRegistry.class),
			beans.getBeanProvider(io.micrometer.core.instrument.MeterRegistry.class),
			beans.getBeanProvider(OpenAiHttpClientBuilderCustomizer.class)
		);
	}

	private static GeneratorHarness createGenerator(
		OpenAIClientAsync client,
		Settings settings
	) {
		OpenAiAnswerAssessmentProperties properties = new OpenAiAnswerAssessmentProperties();
		configureChat(properties, settings.model());
		AnswerAssessmentMetricsRecorder queueMetrics = new NoOpQueueMetrics();
		ExecutorAnswerAssessmentQueueTimeoutScheduler timeoutScheduler =
			new ExecutorAnswerAssessmentQueueTimeoutScheduler();
		AnswerAssessmentRateLimitQueue queue = new AnswerAssessmentRateLimitQueue(
			new AnswerAssessmentQueueProperties(1, Duration.ofSeconds(90)),
			queueMetrics,
			new OpenAiAnswerAssessmentRateLimiter(
				new OpenAiAnswerAssessmentRateLimitProperties(
					400, 400_000, 3_000
				),
				ProviderRateLimitMetricsRecorder.NOOP,
				TimeMeter.SYSTEM_NANOTIME
			),
			timeoutScheduler,
			System::nanoTime
		);
		TokenUsageCollector tokenUsage = new TokenUsageCollector();
		return new GeneratorHarness(
			new OpenAiAnswerAssessmentGenerator(
				client, properties, queue, tokenUsage
			),
			queue,
			timeoutScheduler,
			tokenUsage
		);
	}

	private static void configureChat(OpenAiAnswerAssessmentProperties properties, String model) {
		properties.getChat().setModel(model);
		properties.getChat().setMaxCompletionTokens(300L);
		if (!model.startsWith("gpt-4o")) {
			properties.getChat().setReasoningEffort("none");
			properties.getChat().setVerbosity("low");
		}
	}

	private static List<Observation> execute(
		OpenAiAnswerAssessmentGenerator generator,
		TokenUsageCollector tokenUsage,
		List<EvalCase> cases,
		Settings settings,
		Path rawResults
	) throws IOException {
		List<Observation> observations = new ArrayList<>();
		try (BufferedWriter writer = Files.newBufferedWriter(
			rawResults, StandardCharsets.UTF_8,
			StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
		)) {
			for (int round = 1; round <= settings.rounds(); round++) {
				List<EvalCase> shuffled = new ArrayList<>(cases);
				Collections.shuffle(shuffled, new java.util.Random(settings.seed() + round));
				for (int index = 0; index < shuffled.size(); index++) {
					EvalCase evalCase = shuffled.get(index);
					Observation observation = evaluateOne(generator, tokenUsage, evalCase, round, settings);
					observations.add(observation);
					writer.write(JSON.writer().without(SerializationFeature.INDENT_OUTPUT)
						.writeValueAsString(observation));
					writer.newLine();
					writer.flush();
					System.out.printf("round=%d case=%d/%d id=%s outcome=%s%n",
						round, index + 1, shuffled.size(), evalCase.caseId(),
						observation.errorType() == null ? observation.actualResult() : "ERROR");
				}
			}
		}
		return observations;
	}

	private static Observation evaluateOne(
		OpenAiAnswerAssessmentGenerator generator,
		TokenUsageCollector tokenUsage,
		EvalCase evalCase,
		int round,
		Settings settings
	) {
		long started = System.nanoTime();
		TokenUsageSummary before = tokenUsage.snapshot();
		AnswerAssessmentTask task = generator.generateAsync(evalCase.toInput());
		try {
			AnswerAssessment assessment = task.result().toCompletableFuture()
				.get(settings.timeoutSeconds(), TimeUnit.SECONDS);
			return Observation.success(
				round, evalCase, assessment,
				TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
				TokenUsageSummary.difference(tokenUsage.snapshot(), before)
			);
		} catch (Exception failure) {
			task.cancel();
			Throwable cause = failure.getCause() == null ? failure : failure.getCause();
			return Observation.failure(
				round, evalCase, cause.getClass().getSimpleName(),
				TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
				TokenUsageSummary.difference(tokenUsage.snapshot(), before)
			);
		}
	}

	static List<EvalCase> flattenAndValidate(Dataset dataset, Settings settings) {
		if (!"HUMAN_APPROVED".equals(dataset.reviewStatus()) && !settings.allowUnreviewed()) {
			throw new IllegalStateException(
				"Dataset is not HUMAN_APPROVED. Review labels or run with -PevalAllowUnreviewed=true."
			);
		}
		List<EvalCase> result = new ArrayList<>();
		LinkedHashSet<String> ids = new LinkedHashSet<>();
		for (Question question : dataset.questions()) {
			for (Case candidate : question.cases()) {
				if (candidate.expectedResult() == AnswerResult.UNRECOGNIZED) {
					throw new IllegalArgumentException("UNRECOGNIZED is outside this evaluation: " + candidate.caseId());
				}
				if (!ids.add(candidate.caseId())) {
					throw new IllegalArgumentException("Duplicate caseId: " + candidate.caseId());
				}
				result.add(new EvalCase(question, candidate));
			}
		}
		if (result.isEmpty()) {
			throw new IllegalArgumentException("Evaluation dataset is empty.");
		}
		return List.copyOf(result);
	}

	static Summary summarize(
		Dataset dataset,
		Settings settings,
		List<EvalCase> cases,
		List<Observation> observations
	) {
		return summarize(
			dataset, settings, cases, observations, TokenUsageSummary.empty()
		);
	}

	static Summary summarize(
		Dataset dataset,
		Settings settings,
		List<EvalCase> cases,
		List<Observation> observations,
		TokenUsageSummary tokenUsage
	) {
		long exact = observations.stream().filter(Observation::exactMatch).count();
		long valid = observations.stream().filter(Observation::validResponse).count();
		long criticalFalsePositives = observations.stream()
			.filter(Observation::criticalFalsePositiveOccurred).count();
		double accuracy = ratio(exact, observations.size());
		double validRate = ratio(valid, observations.size());
		Map<AnswerResult, Map<AnswerResult, Long>> confusion = confusion(observations);
		double macroF1 = macroF1(confusion);

		List<GroupMetric> groups = groupMetrics(observations);
		List<CaseMetric> caseMetrics = caseMetrics(cases, observations, settings.rounds());
		List<RoundMetric> roundMetrics = roundMetrics(observations, settings.rounds());
		List<QuestionMetric> questionMetrics = questionMetrics(observations);
		List<LabelMetric> labelMetrics = labelMetrics(confusion);
		List<Gate> gates = new ArrayList<>();
		gates.add(new Gate("overall_exact_accuracy", accuracy, OVERALL_ACCURACY_THRESHOLD, accuracy >= OVERALL_ACCURACY_THRESHOLD));
		gates.add(new Gate("valid_response_rate", validRate, VALID_RESPONSE_THRESHOLD, validRate >= VALID_RESPONSE_THRESHOLD));
		gates.add(new Gate("macro_f1", macroF1, MACRO_F1_THRESHOLD, macroF1 >= MACRO_F1_THRESHOLD));
		gates.add(new Gate("critical_incorrect_to_correct", criticalFalsePositives, 0, criticalFalsePositives == 0));
		groups.forEach(group -> gates.add(new Gate(
			"group_" + group.difficulty() + "_" + group.questionType(),
			group.accuracy(), group.threshold(), group.passed()
		)));
		long failedCases = caseMetrics.stream().filter(metric -> !metric.passed()).count();
		gates.add(new Gate("per_case_repeatability", caseMetrics.size() - failedCases, caseMetrics.size(), failedCases == 0));
		boolean passed = gates.stream().allMatch(Gate::passed);
		return new Summary(
			dataset.datasetVersion(), dataset.reviewStatus(), settings.model(), settings.seed(),
			settings.timeoutSeconds(), settings.rounds(), cases.size(),
			observations.size(), exact, valid, accuracy, validRate, macroF1,
			criticalFalsePositives, tokenUsage, confusion, labelMetrics, roundMetrics,
			questionMetrics, groups, caseMetrics,
			gates, passed
		);
	}

	private static List<RoundMetric> roundMetrics(List<Observation> observations, int rounds) {
		List<RoundMetric> metrics = new ArrayList<>();
		for (int round = 1; round <= rounds; round++) {
			int currentRound = round;
			List<Observation> selected = observations.stream().filter(item -> item.round() == currentRound).toList();
			Map<AnswerResult, Map<AnswerResult, Long>> matrix = confusion(selected);
			metrics.add(new RoundMetric(round, selected.size(),
				ratio(selected.stream().filter(Observation::exactMatch).count(), selected.size()),
				ratio(selected.stream().filter(Observation::validResponse).count(), selected.size()),
				macroF1(matrix), selected.stream().filter(Observation::criticalFalsePositiveOccurred).count(),
				averageLong(selected.stream().mapToLong(Observation::durationMillis).toArray()),
				percentile95(selected.stream().mapToLong(Observation::durationMillis).toArray()),
				usageOf(selected), matrix, labelMetrics(matrix)));
		}
		return List.copyOf(metrics);
	}

	private static List<QuestionMetric> questionMetrics(List<Observation> observations) {
		Map<QuestionRoundKey, List<Observation>> grouped = new LinkedHashMap<>();
		for (Observation observation : observations) {
			grouped.computeIfAbsent(new QuestionRoundKey(observation.round(), observation.questionId()), ignored -> new ArrayList<>())
				.add(observation);
		}
		return grouped.entrySet().stream().map(entry -> {
			List<Observation> items = entry.getValue();
			return new QuestionMetric(entry.getKey().round(), entry.getKey().questionId(), items.size(),
				ratio(items.stream().filter(Observation::exactMatch).count(), items.size()),
				averageNullable(items, Observation::meaningScore), averageNullable(items, Observation::expressionScore),
				averageNullable(items, Observation::grammarScore), averageNullable(items, Observation::totalScore),
				averageLong(items.stream().mapToLong(Observation::durationMillis).toArray()), usageOf(items));
		}).toList();
	}

	private static List<LabelMetric> labelMetrics(Map<AnswerResult, Map<AnswerResult, Long>> matrix) {
		return evaluatedLabels().stream().map(label -> {
			long tp = matrix.get(label).get(label);
			long fp = evaluatedLabels().stream().filter(expected -> expected != label)
				.mapToLong(expected -> matrix.get(expected).get(label)).sum();
			long fn = matrix.get(label).entrySet().stream().filter(entry -> entry.getKey() != label)
				.mapToLong(Map.Entry::getValue).sum();
			double precision = tp + fp == 0 ? 0 : ratio(tp, tp + fp);
			double recall = tp + fn == 0 ? 0 : ratio(tp, tp + fn);
			double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
			return new LabelMetric(label, tp, fp, fn, precision, recall, f1);
		}).toList();
	}

	private static TokenUsageSummary usageOf(List<Observation> observations) {
		return TokenUsageSummary.aggregate(observations.stream().map(Observation::tokenUsage).toList());
	}

	private static double averageNullable(List<Observation> items, java.util.function.Function<Observation, Integer> getter) {
		return items.stream().map(getter).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0);
	}

	private static double averageLong(long[] values) {
		return values.length == 0 ? 0 : java.util.Arrays.stream(values).average().orElse(0);
	}

	private static long percentile95(long[] values) {
		if (values.length == 0) return 0;
		java.util.Arrays.sort(values);
		return values[(int)Math.ceil(values.length * 0.95) - 1];
	}

	private static List<GroupMetric> groupMetrics(List<Observation> observations) {
		List<GroupMetric> groups = new ArrayList<>();
		for (Difficulty difficulty : Difficulty.values()) {
			for (QuestionType type : QuestionType.values()) {
				List<Observation> selected = observations.stream()
					.filter(item -> item.difficulty() == difficulty && item.questionType() == type)
					.toList();
				if (selected.isEmpty()) continue;
				double accuracy = ratio(selected.stream().filter(Observation::exactMatch).count(), selected.size());
				double threshold = switch (difficulty) {
					case EASY -> 0.95;
					case NORMAL -> 0.90;
					case HARD -> 0.85;
				};
				groups.add(new GroupMetric(difficulty, type, selected.size(), accuracy, threshold, accuracy >= threshold));
			}
		}
		return groups;
	}

	private static List<CaseMetric> caseMetrics(List<EvalCase> cases, List<Observation> observations, int rounds) {
		List<CaseMetric> metrics = new ArrayList<>();
		for (EvalCase evalCase : cases) {
			long successes = observations.stream()
				.filter(item -> item.caseId().equals(evalCase.caseId()) && item.exactMatch()).count();
			int required = evalCase.difficulty() == Difficulty.EASY
				? (int)Math.ceil(rounds * 0.90)
				: (int)Math.ceil(rounds * 0.80);
			metrics.add(new CaseMetric(evalCase.caseId(), evalCase.difficulty(), evalCase.questionType(),
				evalCase.expectedResult(), successes, rounds, required, successes >= required));
		}
		return metrics;
	}

	private static Map<AnswerResult, Map<AnswerResult, Long>> confusion(List<Observation> observations) {
		Map<AnswerResult, Map<AnswerResult, Long>> matrix = new EnumMap<>(AnswerResult.class);
		for (AnswerResult expected : evaluatedLabels()) {
			Map<AnswerResult, Long> row = new EnumMap<>(AnswerResult.class);
			for (AnswerResult actual : AnswerResult.values()) row.put(actual, 0L);
			matrix.put(expected, row);
		}
		for (Observation item : observations) {
			if (item.actualResult() != null) {
				matrix.get(item.expectedResult()).merge(item.actualResult(), 1L, Long::sum);
			}
		}
		return matrix;
	}

	private static double macroF1(Map<AnswerResult, Map<AnswerResult, Long>> matrix) {
		double total = 0;
		for (AnswerResult label : evaluatedLabels()) {
			long tp = matrix.get(label).get(label);
			long fp = evaluatedLabels().stream().filter(expected -> expected != label)
				.mapToLong(expected -> matrix.get(expected).get(label)).sum();
			long fn = matrix.get(label).entrySet().stream().filter(entry -> entry.getKey() != label)
				.mapToLong(Map.Entry::getValue).sum();
			double precision = tp + fp == 0 ? 0 : ratio(tp, tp + fp);
			double recall = tp + fn == 0 ? 0 : ratio(tp, tp + fn);
			total += precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
		}
		return total / evaluatedLabels().size();
	}

	private static List<AnswerResult> evaluatedLabels() {
		return List.of(AnswerResult.CORRECT, AnswerResult.PARTIALLY_CORRECT, AnswerResult.INCORRECT);
	}

	private static double ratio(long numerator, long denominator) {
		return denominator == 0 ? 0 : (double)numerator / denominator;
	}

	private static String markdown(Summary summary) {
		StringBuilder out = new StringBuilder("# OpenAI 답변 평가 반복 실행 결과\n\n");
		out.append("- 최종 판정: **").append(summary.passed() ? "PASS" : "FAIL").append("**\n");
		out.append("- 데이터셋: `").append(summary.datasetVersion()).append("` (`").append(summary.reviewStatus()).append("`)\n");
		out.append("- 모델: `").append(summary.model()).append("`\n");
		out.append("- 순서 seed: `").append(summary.seed()).append("`\n");
		out.append("- 호출 제한시간: ").append(summary.timeoutSeconds()).append("초\n");
		out.append("- 실행: ").append(summary.caseCount()).append(" cases x ").append(summary.rounds()).append(" rounds = ").append(summary.observationCount()).append(" calls\n");
		out.append("- 정확도: ").append(percent(summary.accuracy())).append("\n");
		out.append("- 유효 응답률: ").append(percent(summary.validResponseRate())).append("\n");
		out.append("- Macro-F1: ").append(String.format(Locale.ROOT, "%.3f", summary.macroF1())).append("\n");
		out.append("- 중요 오답→정답 오판: ").append(summary.criticalFalsePositives()).append("\n\n");
		out.append("## 라운드별 평가\n\n| 라운드 | 정확도 | Macro-F1 | 유효 응답률 | 평균 지연(ms) | p95(ms) | 전체 토큰 |\n|---:|---:|---:|---:|---:|---:|---:|\n");
		for (RoundMetric round : summary.roundMetrics()) {
			out.append('|').append(round.round()).append('|').append(percent(round.accuracy())).append('|')
				.append(String.format(Locale.ROOT, "%.3f", round.macroF1())).append('|').append(percent(round.validResponseRate())).append('|')
				.append(String.format(Locale.ROOT, "%.1f", round.averageDurationMillis())).append('|').append(round.p95DurationMillis()).append('|')
				.append(round.tokenUsage().totalTokens()).append("|\n");
		}
		out.append("\n## 라벨별 전체 평가\n\n| 라벨 | 정밀도 | 재현율 | F1 | TP | FP | FN |\n|---|---:|---:|---:|---:|---:|---:|\n");
		for (LabelMetric label : summary.labelMetrics()) {
			out.append('|').append(label.label()).append('|').append(percent(label.precision())).append('|')
				.append(percent(label.recall())).append('|').append(String.format(Locale.ROOT, "%.3f", label.f1())).append('|')
				.append(label.truePositive()).append('|').append(label.falsePositive()).append('|').append(label.falseNegative()).append("|\n");
		}
		out.append("## 토큰 사용량\n\n");
		out.append("- 사용량이 기록된 호출: ").append(summary.tokenUsage().recordedCalls()).append("\n");
		out.append("- 입력 토큰: ").append(summary.tokenUsage().promptTokens()).append("\n");
		out.append("- 출력 토큰: ").append(summary.tokenUsage().completionTokens()).append("\n");
		out.append("- 캐시 입력 토큰: ").append(summary.tokenUsage().cachedTokens()).append("\n");
		out.append("- Reasoning 토큰: ").append(summary.tokenUsage().reasoningTokens()).append("\n");
		out.append("- 전체 토큰: ").append(summary.tokenUsage().totalTokens()).append("\n");
		out.append("- 호출당 평균 전체 토큰: ").append(String.format(
			Locale.ROOT, "%.1f", summary.tokenUsage().averageTotalTokens()
		)).append("\n");
		out.append("- 호출당 전체 토큰 범위: ").append(summary.tokenUsage().minimumTotalTokens())
			.append(" ~ ").append(summary.tokenUsage().maximumTotalTokens()).append("\n\n");
		out.append("## 통과 기준\n\n| 기준 | 결과 | 임계값 | 판정 |\n|---|---:|---:|---|\n");
		for (Gate gate : summary.gates()) {
			out.append('|').append(gate.name()).append('|').append(String.format(Locale.ROOT, "%.3f", gate.actual()))
				.append('|').append(String.format(Locale.ROOT, "%.3f", gate.threshold())).append('|')
				.append(gate.passed() ? "PASS" : "FAIL").append("|\n");
		}
		out.append("\n## 난이도 x 문제 유형\n\n| 난이도 | 유형 | 정확도 | 기준 | 판정 |\n|---|---|---:|---:|---|\n");
		for (GroupMetric group : summary.groups()) {
			out.append('|').append(group.difficulty()).append('|').append(group.questionType()).append('|')
				.append(percent(group.accuracy())).append('|').append(percent(group.threshold())).append('|')
				.append(group.passed() ? "PASS" : "FAIL").append("|\n");
		}
		out.append("\n## 반복 안정성 미통과 케이스\n\n");
		List<CaseMetric> failed = summary.cases().stream().filter(metric -> !metric.passed()).toList();
		if (failed.isEmpty()) out.append("없음\n");
		else failed.forEach(metric -> out.append("- `").append(metric.caseId()).append("`: ")
			.append(metric.successes()).append('/').append(metric.rounds()).append(" (required ")
			.append(metric.requiredSuccesses()).append(")\n"));
		return out.toString();
	}

	private static String percent(double value) {
		return String.format(Locale.ROOT, "%.1f%%", value * 100);
	}

	record Settings(Path dataset, Path outputDirectory, int rounds, long seed, long timeoutSeconds,
		String model, boolean allowUnreviewed) {
		static Settings fromSystemProperties() {
			int rounds = Integer.parseInt(System.getProperty("eval.rounds", "10"));
			long timeout = Long.parseLong(System.getProperty("eval.timeout-seconds", "90"));
			if (rounds < 1 || timeout < 1) throw new IllegalArgumentException("rounds and timeout must be positive.");
			return new Settings(
				Path.of(System.getProperty("eval.dataset")), Path.of(System.getProperty("eval.output-dir")),
				rounds, Long.parseLong(System.getProperty("eval.seed", "20260826")), timeout,
				System.getProperty("eval.model", "gpt-5.6-terra"),
				Boolean.parseBoolean(System.getProperty("eval.allow-unreviewed", "false"))
			);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Dataset(String datasetVersion, String reviewStatus, List<Question> questions) {
		Dataset { questions = List.copyOf(Objects.requireNonNull(questions)); }
	}
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Question(long questionId, Difficulty difficulty, QuestionType questionType, String questionText,
		String questionTextKo, String gradingContext, String modelAnswer, List<String> acceptedAnswers, List<Case> cases) {
		Question { acceptedAnswers = acceptedAnswers == null ? List.of() : List.copyOf(acceptedAnswers); cases = List.copyOf(cases); }
	}
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Case(String caseId, String answerText, AnswerResult expectedResult, boolean criticalFalsePositive) {}
	record EvalCase(Question question, Case candidate) {
		String caseId() { return candidate.caseId(); }
		Difficulty difficulty() { return question.difficulty(); }
		QuestionType questionType() { return question.questionType(); }
		AnswerResult expectedResult() { return candidate.expectedResult(); }
		AnswerAssessmentInput toInput() { return new AnswerAssessmentInput(question.difficulty(), question.questionType(),
			question.questionText(), question.questionTextKo(), question.gradingContext(), question.modelAnswer(),
			new LinkedHashSet<>(question.acceptedAnswers()), candidate.answerText()); }
	}
	record Observation(int round, long questionId, String caseId, Difficulty difficulty, QuestionType questionType,
		AnswerResult expectedResult, AnswerResult actualResult, boolean criticalFalsePositive,
		Integer meaningScore, Integer expressionScore, Integer grammarScore, Integer totalScore,
		long durationMillis, String errorType, TokenUsageSummary tokenUsage) {
		static Observation success(int round, EvalCase c, AnswerAssessment a, long duration, TokenUsageSummary usage) {
			return new Observation(round, c.question().questionId(), c.caseId(), c.difficulty(), c.questionType(), c.expectedResult(), a.result(),
				c.candidate().criticalFalsePositive(), a.meaningScore(), a.expressionScore(), a.grammarScore(), a.totalScore(), duration, null, usage);
		}
		static Observation success(int round, EvalCase c, AnswerAssessment a, long duration) {
			return success(round, c, a, duration, TokenUsageSummary.empty());
		}
		static Observation failure(int round, EvalCase c, String error, long duration, TokenUsageSummary usage) {
			return new Observation(round, c.question().questionId(), c.caseId(), c.difficulty(), c.questionType(), c.expectedResult(), null,
				c.candidate().criticalFalsePositive(), null, null, null, null, duration, error, usage);
		}
		boolean validResponse() { return errorType == null; }
		boolean exactMatch() { return validResponse() && expectedResult == actualResult; }
		boolean criticalFalsePositiveOccurred() { return criticalFalsePositive && actualResult == AnswerResult.CORRECT && expectedResult != AnswerResult.CORRECT; }
	}
	record GroupMetric(Difficulty difficulty, QuestionType questionType, int observations, double accuracy, double threshold, boolean passed) {}
	record CaseMetric(String caseId, Difficulty difficulty, QuestionType questionType, AnswerResult expectedResult,
		long successes, int rounds, int requiredSuccesses, boolean passed) {}
	record Gate(String name, double actual, double threshold, boolean passed) {}
	record LabelMetric(AnswerResult label, long truePositive, long falsePositive, long falseNegative,
		double precision, double recall, double f1) {}
	record RoundMetric(int round, int observations, double accuracy, double validResponseRate, double macroF1,
		long criticalFalsePositives, double averageDurationMillis, long p95DurationMillis,
		TokenUsageSummary tokenUsage, Map<AnswerResult, Map<AnswerResult, Long>> confusionMatrix,
		List<LabelMetric> labelMetrics) {}
	record QuestionRoundKey(int round, long questionId) {}
	record QuestionMetric(int round, long questionId, int cases, double exactAccuracy,
		double averageMeaningScore, double averageExpressionScore, double averageGrammarScore,
		double averageTotalScore, double averageDurationMillis, TokenUsageSummary tokenUsage) {}
	record Summary(String datasetVersion, String reviewStatus, String model, long seed, long timeoutSeconds,
		int rounds, int caseCount, int observationCount,
		long exactMatches, long validResponses, double accuracy, double validResponseRate, double macroF1,
		long criticalFalsePositives, TokenUsageSummary tokenUsage,
		Map<AnswerResult, Map<AnswerResult, Long>> confusionMatrix, List<LabelMetric> labelMetrics,
		List<RoundMetric> roundMetrics, List<QuestionMetric> questionMetrics,
		List<GroupMetric> groups, List<CaseMetric> cases, List<Gate> gates, boolean passed) {}
	record TokenUsageSummary(long recordedCalls, long promptTokens, long completionTokens,
		long totalTokens, long cachedTokens, long reasoningTokens,
		long minimumTotalTokens, long maximumTotalTokens, double averageTotalTokens) {
		static TokenUsageSummary empty() {
			return new TokenUsageSummary(0, 0, 0, 0, 0, 0, 0, 0, 0);
		}
		static TokenUsageSummary difference(TokenUsageSummary after, TokenUsageSummary before) {
			long calls = after.recordedCalls - before.recordedCalls;
			long total = after.totalTokens - before.totalTokens;
			return new TokenUsageSummary(calls, after.promptTokens - before.promptTokens,
				after.completionTokens - before.completionTokens, total,
				after.cachedTokens - before.cachedTokens, after.reasoningTokens - before.reasoningTokens,
				calls == 0 ? 0 : total, calls == 0 ? 0 : total, calls == 0 ? 0 : (double)total / calls);
		}
		static TokenUsageSummary aggregate(List<TokenUsageSummary> values) {
			long calls = values.stream().mapToLong(TokenUsageSummary::recordedCalls).sum();
			long prompt = values.stream().mapToLong(TokenUsageSummary::promptTokens).sum();
			long completion = values.stream().mapToLong(TokenUsageSummary::completionTokens).sum();
			long total = values.stream().mapToLong(TokenUsageSummary::totalTokens).sum();
			long cached = values.stream().mapToLong(TokenUsageSummary::cachedTokens).sum();
			long reasoning = values.stream().mapToLong(TokenUsageSummary::reasoningTokens).sum();
			long min = values.stream().filter(v -> v.recordedCalls > 0).mapToLong(TokenUsageSummary::minimumTotalTokens).min().orElse(0);
			long max = values.stream().mapToLong(TokenUsageSummary::maximumTotalTokens).max().orElse(0);
			return new TokenUsageSummary(calls, prompt, completion, total, cached, reasoning, min, max, calls == 0 ? 0 : (double)total / calls);
		}
	}
	record GeneratorHarness(
		OpenAiAnswerAssessmentGenerator generator,
		AnswerAssessmentRateLimitQueue queue,
		ExecutorAnswerAssessmentQueueTimeoutScheduler timeoutScheduler,
		TokenUsageCollector tokenUsage
	) {}

	private static final class NoOpQueueMetrics implements AnswerAssessmentMetricsRecorder {
		public void bind(java.util.function.IntSupplier value, int capacity) {}
		public void recordAccepted() {}
		public void recordRejected() {}
		public void recordCompleted() {}
		public void recordFailed() {}
		public void recordQueued() {}
		public void recordPromoted() {}
		public void recordQueueFull() {}
		public void recordQueueTimeout() {}
		public void recordQueueCancelled() {}
		public void recordQueueWait(QueueWaitResult result, Duration duration) {}
	}
	private static final class TokenUsageCollector implements OpenAiAnswerAssessmentMetricsRecorder {
		private long calls;
		private long promptTokens;
		private long completionTokens;
		private long totalTokens;
		private long cachedTokens;
		private long reasoningTokens;
		private long minimumTotalTokens = Long.MAX_VALUE;
		private long maximumTotalTokens;

		public synchronized void recordTokenUsage(
			long prompt,
			long completion,
			long total,
			long cached,
			long reasoning
		) {
			calls++;
			promptTokens += prompt;
			completionTokens += completion;
			totalTokens += total;
			cachedTokens += cached;
			reasoningTokens += reasoning;
			minimumTotalTokens = Math.min(minimumTotalTokens, total);
			maximumTotalTokens = Math.max(maximumTotalTokens, total);
		}
		public void recordFailure(FailureReason reason) {}
		public void recordFailure(Throwable failure) {}

		synchronized TokenUsageSummary snapshot() {
			if (calls == 0) return TokenUsageSummary.empty();
			return new TokenUsageSummary(
				calls, promptTokens, completionTokens, totalTokens, cachedTokens,
				reasoningTokens, minimumTotalTokens, maximumTotalTokens,
				(double)totalTokens / calls
			);
		}
	}
}
