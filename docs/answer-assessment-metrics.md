# 답안 평가 동시성·대기열 지표

`AnswerAssessmentConcurrencyLimiter`는 OpenAI 답안 평가의 실제 실행 수와
대기열을 함께 제한한다. 기본값은 active 32건, queue 64건, 최대 대기 10초이며
active와 queue를 합친 즉시 수용 가능량은 96건이다. 대기는 request thread,
평가 executor thread, DB connection을 점유하지 않는 `CompletableFuture` 기반이다.

| 지표 | 종류 | 의미 |
| --- | --- | --- |
| `malhaebom.answer.assessment.active` | Gauge | 현재 permit을 점유하고 평가 중인 작업 수 |
| `malhaebom.answer.assessment.limit` | Gauge | 허용된 최대 동시 평가 수. 기본값 32 |
| `malhaebom.answer.assessment.queue.size` | Gauge | 현재 FIFO 대기열에 남아 있는 작업 수 |
| `malhaebom.answer.assessment.queue.capacity` | Gauge | 대기열 최대 용량. 기본값 64 |
| `malhaebom.answer.assessment.accepted` | Counter | direct admission 또는 queue 승격 후 실제 평가를 시작한 누적 수 |
| `malhaebom.answer.assessment.rejected` | Counter | queue full 또는 queue timeout으로 provider 호출 전에 거절된 누적 수 |
| `malhaebom.answer.assessment.completed` | Counter | permit을 점유한 비동기 작업이 정상 완료된 누적 수 |
| `malhaebom.answer.assessment.failed` | Counter | permit 획득 후 작업 생성 또는 비동기 완료가 예외로 끝난 누적 수 |
| `malhaebom.answer.assessment.queued` | Counter | FIFO 대기열에 들어간 누적 수 |
| `malhaebom.answer.assessment.queue.promoted` | Counter | 대기열에서 permit으로 승격되어 평가를 시작한 누적 수 |
| `malhaebom.answer.assessment.queue.full` | Counter | 대기열이 가득 차 즉시 503으로 거절된 누적 수 |
| `malhaebom.answer.assessment.queue.timeout` | Counter | 최대 대기 시간을 넘겨 503으로 종료된 누적 수 |
| `malhaebom.answer.assessment.queue.cancelled` | Counter | 대기 중 원래 제출 deadline 또는 호출자 취소로 제거된 누적 수 |
| `malhaebom.answer.assessment.queue.wait` | Timer | 대기 종료까지 걸린 시간. `result=promoted`, `timeout`, `cancelled`, `shutdown` 태그로 원인을 구분 |

`rejected`에는 `queue.full`과 `queue.timeout`이 포함되지만 대기 중 취소는 포함되지
않는다. 이 세 경우 모두 provider 호출 전에 끝난다. HTTP 503만으로는 queue full과
queue timeout을 구분할 수 없으므로 서버 카운터를 함께 확인해야 한다.

운영 및 부하 테스트에서 다음 불변식을 확인한다.

- `active <= limit`
- `queue.size <= queue.capacity`
- permit을 얻은 작업은 정확히 한 번 `completed` 또는 `failed`로 끝난다.
- `accepted`는 실제 평가 시작 수이므로 direct admission과 승격을 모두 포함한다.
- 단계 종료 후 `active=0`, `queue.size=0`, Hikari pending=0으로 회복한다.
- `queue.full`, `queue.timeout`, `queue.cancelled`는 외부 provider 호출 수가 아니다.

Counter는 애플리케이션 프로세스가 시작된 뒤의 누적값이고 재시작 시 초기화된다.
부하 리포트는 각 단계의 첫 snapshot과 마지막 snapshot 차이로 queue 종료 원인 수를
계산한다. Prometheus에서는 점(`.`)이 밑줄(`_`)로 바뀌고 Counter에는 `_total`
접미사가 붙는다. 예를 들어 `malhaebom.answer.assessment.queue.full`은
`malhaebom_answer_assessment_queue_full_total`로 노출된다.
