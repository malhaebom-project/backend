import { sleep } from 'k6';
import { Counter, Gauge } from 'k6/metrics';

const summaryPath = __ENV.STAGE_SUMMARY;
if (!summaryPath) {
  throw new Error('STAGE_SUMMARY environment variable is required.');
}

const document = JSON.parse(open(summaryPath));
const stageSummary = new Gauge('loadtest_stage_summary');
const stageEventOffset = new Gauge('loadtest_stage_event_offset_seconds');
const loadtestEvent = new Counter('loadtest_event');

export const options = {
  scenarios: {
    publish: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '10s',
    },
  },
};

export default function publish() {
  loadtestEvent.add(0, { event: 'recovery_complete' });
  // Flush the baseline and increment separately so Prometheus can detect it.
  sleep(1.1);
  loadtestEvent.add(1, { event: 'recovery_complete' });
  Object.entries(document.summary).forEach(([metric, value]) => {
    if (Number.isFinite(Number(value))) {
      stageSummary.add(Number(value), { metric });
    }
  });
  Object.entries(document.events).forEach(([event, value]) => {
    if (Number.isFinite(Number(value))) {
      stageEventOffset.add(Number(value), { event });
    }
  });
  // Keep the process alive until the increment and summary gauges are flushed.
  sleep(1.1);
}
