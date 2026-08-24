#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd -- "${script_dir}/../.." && pwd)"

cd "${repository_root}"

export LOADTEST_ASSESSMENT_LIMIT="${LOADTEST_ASSESSMENT_LIMIT:-32}"
export LOADTEST_ASSESSMENT_QUEUE_CAPACITY="${LOADTEST_ASSESSMENT_QUEUE_CAPACITY:-64}"
export LOADTEST_ASSESSMENT_MAX_QUEUE_WAIT="${LOADTEST_ASSESSMENT_MAX_QUEUE_WAIT:-10s}"

docker compose \
  -f docker-compose.prod.yml \
  -f docker-compose.loadtest.yml \
  config --quiet

docker compose \
  -f docker-compose.prod.yml \
  -f docker-compose.loadtest.yml \
  up -d --force-recreate

echo "Load-test Compose stack started."
echo "Assessment limit: ${LOADTEST_ASSESSMENT_LIMIT}"
echo "Queue capacity: ${LOADTEST_ASSESSMENT_QUEUE_CAPACITY}"
echo "Max queue wait: ${LOADTEST_ASSESSMENT_MAX_QUEUE_WAIT}"
