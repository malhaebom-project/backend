# 답안 평가 rate limit·대기열 지표

`AnswerAssessmentRateLimitQueue`는 OpenAI rate token이 부족한 요청을 최대 64건,
10초 동안 FIFO로 대기시킨다. 대기는 request thread, 평가 executor thread,
DB connection을 점유하지 않는 `CompletableFuture` 기반이다. queue 상태와 전이는
rate limit 획득 결과에 따라 관리되고, Micrometer meter 등록과 기록은
`MicrometerAnswerAssessmentMetricsRecorder`가 담당한다.

실제 provider 요청이 시작된 뒤의 사용량과 실패 원인은 별도
`OpenAiAnswerAssessmentMetricsRecorder`가 기록한다. 따라서 queue full·queue
timeout처럼 OpenAI 호출 전에 종료된 작업은 OpenAI 실패에 포함되지 않는다.

| 지표 | 종류 | 의미 |
| --- | --- | --- |
| `malhaebom.answer.assessment.queue.size` | Gauge | 현재 FIFO 대기열에 남아 있는 작업 수 |
| `malhaebom.answer.assessment.queue.capacity` | Gauge | 대기열 최대 용량. 기본값 64 |
| `malhaebom.answer.assessment.accepted` | Counter | direct admission 또는 queue 승격 후 실제 평가를 시작한 누적 수 |
| `malhaebom.answer.assessment.rejected` | Counter | queue full 또는 queue timeout으로 provider 호출 전에 거절된 누적 수 |
| `malhaebom.answer.assessment.completed` | Counter | 시작된 비동기 작업이 정상 완료된 누적 수 |
| `malhaebom.answer.assessment.failed` | Counter | 작업 생성 또는 비동기 완료가 예외로 끝난 누적 수 |
| `malhaebom.answer.assessment.queued` | Counter | FIFO 대기열에 들어간 누적 수 |
| `malhaebom.answer.assessment.queue.promoted` | Counter | 대기열에서 rate token을 획득해 평가를 시작한 누적 수 |
| `malhaebom.answer.assessment.queue.full` | Counter | 대기열이 가득 차 즉시 503으로 거절된 누적 수 |
| `malhaebom.answer.assessment.queue.timeout` | Counter | 최대 대기 시간을 넘겨 503으로 종료된 누적 수 |
| `malhaebom.answer.assessment.queue.cancelled` | Counter | 대기 중 원래 제출 deadline 또는 호출자 취소로 제거된 누적 수 |
| `malhaebom.answer.assessment.queue.wait` | Timer | 대기 종료까지 걸린 시간. `result=promoted`, `timeout`, `cancelled`, `shutdown` 태그로 원인을 구분 |

## Bucket4j provider rate limit

| 지표 | 종류 | 태그 | 의미 |
| --- | --- | --- | --- |
| `malhaebom.ai.provider.rate.limit.capacity` | Gauge | `provider=openai`, `quota=requests`, `tokens` | 실행 중인 설정에서 생성한 요청 bucket과 추정 token bucket의 최대 용량 |
| `malhaebom.ai.provider.rate.limit.available` | Gauge | `provider=openai`, `quota=requests`, `tokens` | 요청 bucket과 추정 token bucket의 현재 잔여량 |
| `malhaebom.ai.provider.rate.limit.requests` | Counter | `provider=openai`, `result=allowed`, `delayed`, `rejected` | 즉시 허용, refill 대기, queue 수용 실패로 결정된 누적 수 |

`tokens` 잔여량은 실제 응답 usage가 아니라 요청 시작 전에
`tokens-per-request` 설정값으로 예약하는 추정 quota다. 실제 과금 토큰은 아래의
`malhaebom.openai.answer.assessment.tokens`로 확인한다.

## OpenAI 사용량·실패 원인

| 지표 | 종류 | 태그 | 의미 |
| --- | --- | --- | --- |
| `malhaebom.openai.answer.assessment.tokens` | Counter | `type=prompt`, `completion`, `total`, `cached`, `reasoning` | OpenAI 응답 usage에 포함된 누적 토큰 수 |
| `malhaebom.openai.answer.assessment.failures` | Counter | `reason=rate_limit`, `timeout`, `authentication`, `permission`, `bad_request`, `server_error`, `io_error`, `cancelled`, `refusal`, `empty_response`, `invalid_response`, `unknown` | 실제 OpenAI 요청 또는 응답 처리 실패의 누적 수 |

토큰은 provider 응답을 받은 즉시 기록한다. 따라서 구조화 응답 파싱 실패나
refusal로 최종 채점이 실패해도 이미 소비된 토큰은 빠지지 않는다. `cached`는
prompt 토큰의 부분집합이고 `reasoning`은 completion 토큰의 부분집합이므로 다섯
시계열을 서로 합산해 총비용으로 해석하면 안 된다. 전체 사용량은 `type=total`,
입출력 비용 분석은 `prompt`와 `completion`, 캐시·추론 비중 분석은 `cached`와
`reasoning`을 각각 확인한다. usage 필드 정의는
[OpenAI Chat Completions API reference](https://developers.openai.com/api/reference/cli/resources/chat/subresources/completions)를 따른다.

`rejected`에는 `queue.full`과 `queue.timeout`이 포함되지만 대기 중 취소는 포함되지
않는다. 이 세 경우 모두 provider 호출 전에 끝난다. HTTP 503만으로는 queue full과
queue timeout을 구분할 수 없으므로 서버 카운터를 함께 확인해야 한다.

운영 및 부하 테스트에서 다음 불변식을 확인한다.

- `queue.size <= queue.capacity`
- 시작된 작업은 정확히 한 번 `completed` 또는 `failed`로 끝난다.
- `accepted`는 실제 평가 시작 수이므로 direct admission과 승격을 모두 포함한다.
- 단계 종료 후 `queue.size=0`, Hikari pending=0으로 회복한다.
- `queue.full`, `queue.timeout`, `queue.cancelled`는 외부 provider 호출 수가 아니다.
- OpenAI failure reason 하나는 실패한 실제 provider 요청당 정확히 한 번 증가한다.
- provider 응답을 받은 작업은 후속 파싱 결과와 관계없이 usage를 한 번 기록한다.

Counter는 애플리케이션 프로세스가 시작된 뒤의 누적값이고 재시작 시 초기화된다.
부하 리포트는 각 단계의 첫 snapshot과 마지막 snapshot 차이로 queue 종료 원인 수를
계산한다. Prometheus에서는 점(`.`)이 밑줄(`_`)로 바뀌고 Counter에는 `_total`
접미사가 붙는다. 예를 들어 `malhaebom.answer.assessment.queue.full`은
`malhaebom_answer_assessment_queue_full_total`로 노출된다.
