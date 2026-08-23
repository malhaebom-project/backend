import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const manifestPath = __ENV.MANIFEST;
const concurrency = Number(__ENV.CONCURRENCY || '10');
const assessmentLimit = Number(__ENV.ASSESSMENT_LIMIT || '48');
const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const probeRate = Number(__ENV.PROBE_RATE || '20');
const summaryPath = __ENV.SUMMARY_PATH || 'load-tests/results/summary.json';

if (!manifestPath) {
  throw new Error('MANIFEST environment variable is required.');
}

const manifest = JSON.parse(open(manifestPath));
const stage = manifest.stages.find((candidate) =>
  Number(candidate.concurrency) === concurrency
);

if (!stage || stage.fixtures.length !== concurrency) {
  throw new Error(
    `Manifest does not contain exactly ${concurrency} fixtures.`
  );
}

const answerSuccess = new Counter('answer_success');
const answerAttempts = new Counter('answer_attempts');
const answerClassified = new Counter('answer_classified');
const expectedOverload = new Counter('answer_expected_overload');
const unexpectedResponse = new Counter('answer_unexpected_response');
const responseMismatch = new Counter('answer_response_mismatch');
const answerSuccessDuration = new Trend('answer_success_duration', true);
const overloadDuration = new Trend('answer_overload_duration', true);
const probeSuccess = new Rate('probe_success');
const probeDuration = new Trend('probe_duration', true);
const baselineProbeSuccess = new Rate('probe_baseline_success');
const baselineProbeDuration = new Trend('probe_baseline_duration', true);

const thresholds = {
  answer_unexpected_response: ['count==0'],
  answer_response_mismatch: ['count==0'],
  answer_classified: [`count==${concurrency}`],
  probe_baseline_success: ['rate==1'],
  answer_success_duration: ['max<=30000'],
  probe_success: ['rate==1'],
};

if (concurrency > assessmentLimit) {
  thresholds.answer_success = ['count>0'];
  thresholds.answer_expected_overload = ['count>0'];
  thresholds.answer_overload_duration = ['p(95)<=5000'];
} else {
  thresholds.answer_success = [`count==${concurrency}`];
}

export const options = {
  discardResponseBodies: false,
  scenarios: {
    probe_baseline: {
      executor: 'constant-arrival-rate',
      exec: 'baselineProbe',
      rate: probeRate,
      timeUnit: '1s',
      duration: '10s',
      preAllocatedVUs: Math.max(10, probeRate),
      maxVUs: Math.max(50, probeRate * 3),
    },
    probe_under_load: {
      executor: 'constant-arrival-rate',
      exec: 'probe',
      rate: probeRate,
      timeUnit: '1s',
      duration: '35s',
      startTime: '12s',
      preAllocatedVUs: Math.max(10, probeRate),
      maxVUs: Math.max(50, probeRate * 3),
    },
    answer_burst: {
      executor: 'per-vu-iterations',
      exec: 'submitAnswer',
      vus: concurrency,
      iterations: 1,
      startTime: '14s',
      maxDuration: '33s',
      gracefulStop: '0s',
    },
  },
  thresholds,
};

export function submitAnswer() {
  const fixtureIndex = exec.scenario.iterationInTest;
  const fixture = stage.fixtures[fixtureIndex];
  answerAttempts.add(1);
  if (!fixture) {
    unexpectedResponse.add(1);
    throw new Error(`No fixture for iteration ${fixtureIndex}.`);
  }

  const response = http.post(
    `${baseUrl}/api/v1/learning-sessions/${fixture.sessionId}`
      + `/questions/${fixture.sessionQuestionId}/answers`,
    JSON.stringify({ speechAnswerId: fixture.speechAnswerId }),
    {
      headers: { 'Content-Type': 'application/json' },
      redirects: 0,
      timeout: '35s',
      tags: { name: 'answer_submission' },
    }
  );

  const body = parseJson(response);
  if (response.status === 200) {
    answerClassified.add(1);
    const matchesFixture = body !== null
      && body.success === true
      && body.data !== null
      && Number(body.data.sessionQuestionId)
        === Number(fixture.sessionQuestionId)
      && body.data.answerId !== null;
    answerSuccess.add(1);
    answerSuccessDuration.add(response.timings.duration);
    if (!matchesFixture) {
      responseMismatch.add(1);
    }
    check(response, {
      'successful answer belongs to its fixture': () => matchesFixture,
    });
    return;
  }

  if (
    response.status === 503
    && body !== null
    && body.errorCode === 'ANSWER_ASSESSMENT_OVERLOADED'
  ) {
    answerClassified.add(1);
    expectedOverload.add(1);
    overloadDuration.add(response.timings.duration);
    return;
  }

  answerClassified.add(1);
  unexpectedResponse.add(1, {
    status: String(response.status),
    error_code: body && body.errorCode ? body.errorCode : 'NONE',
  });
}

export function probe() {
  executeProbe(probeSuccess, probeDuration);
}

export function baselineProbe() {
  executeProbe(baselineProbeSuccess, baselineProbeDuration);
}

function executeProbe(successMetric, durationMetric) {
  const response = http.get(`${baseUrl}/api/v1/question-types`, {
    redirects: 0,
    timeout: '3s',
    tags: { name: 'question_types_probe' },
  });
  const success = response.status === 200;
  successMetric.add(success);
  durationMetric.add(response.timings.duration);
  check(response, { 'probe API responds with 200': () => success });
}

function parseJson(response) {
  try {
    return response.json();
  } catch (error) {
    return null;
  }
}

export function handleSummary(data) {
  const summary = {
    runId: manifest.runId,
    concurrency,
    generatedAt: new Date().toISOString(),
    metrics: data.metrics,
    rootGroup: data.root_group,
  };
  return {
    [summaryPath]: JSON.stringify(summary, null, 2),
    stdout: summaryLine(data),
  };
}

function summaryLine(data) {
  const value = (name, field = 'count') =>
    data.metrics[name] && data.metrics[name].values
      ? data.metrics[name].values[field] || 0
      : 0;
  return [
    `answer load stage=${concurrency}`,
    `success=${value('answer_success')}`,
    `overload=${value('answer_expected_overload')}`,
    `unexpected=${value('answer_unexpected_response')}`,
    `mismatch=${value('answer_response_mismatch')}`,
    `baseline_probe_p95=${value('probe_baseline_duration', 'p(95)')}`,
    `loaded_probe_p95=${value('probe_duration', 'p(95)')}`,
    `probe_rate=${value('probe_success', 'rate')}`,
  ].join(' ') + '\n';
}
