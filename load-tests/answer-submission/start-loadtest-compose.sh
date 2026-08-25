#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd -- "${script_dir}/../.." && pwd)"

cd "${repository_root}"

export LOADTEST_ASSESSMENT_QUEUE_CAPACITY="${LOADTEST_ASSESSMENT_QUEUE_CAPACITY:-64}"
export LOADTEST_ASSESSMENT_MAX_QUEUE_WAIT="${LOADTEST_ASSESSMENT_MAX_QUEUE_WAIT:-10s}"
export LOADTEST_BACKEND_IMAGE="${LOADTEST_BACKEND_IMAGE:-malhaebom/backend:latest}"

docker compose \
  -f docker-compose.prod.yml \
  -f docker-compose.loadtest.yml \
  config --quiet

docker compose \
  -f docker-compose.prod.yml \
  -f docker-compose.loadtest.yml \
  pull was

docker compose \
  -f docker-compose.prod.yml \
  -f docker-compose.loadtest.yml \
  up -d --force-recreate --remove-orphans

echo "Load-test WAS started."
echo "Backend image: ${LOADTEST_BACKEND_IMAGE}"
echo "Queue capacity: ${LOADTEST_ASSESSMENT_QUEUE_CAPACITY}"
echo "Max queue wait: ${LOADTEST_ASSESSMENT_MAX_QUEUE_WAIT}"
