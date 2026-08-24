# 답안 제출 비동기 부하 테스트

실제 답안 제출 HTTP 경로에 같은 텍스트를 가진 서로 다른 세션을 병렬로
전송한다. OpenAI 응답 대기 중 Tomcat 요청 스레드가 반환되는지, active 32건과
FIFO queue 64건의 경계가 지켜지는지, 동시에 호출한 다른 API가 영향을 받는지
확인한다.

## 용어

### 테스트 실행

| 용어                | 의미                                                                                                                      |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------- |
| VU (Virtual User) | 실제 사용자 한 명을 대신해 요청을 보내는 k6 가상 사용자. 이 테스트에서는 각 VU가 답안을 한 번 제출한다.                                                         |
| Fixture           | 테스트 전에 DB에 준비하는 테스트용 데이터. 실제 H2 또는 Supabase에 생성하며 mock 응답이 아니다.                                                         |
| VU fixture        | VU 한 명에게만 할당한 학습 세션, 세션 문제와 완료된 `SpeechAnswer` ID 묶음. 사용자별 상태 충돌을 막고 응답이 서로 섞였는지 검증하는 데 사용한다.                           |
| Concurrency (동시성) | 같은 순간에 완료되지 않고 진행 중인 작업 수. 전체 요청 수나 초당 처리량(TPS)과는 다른 값이다.                                                               |
| Burst             | 여러 VU가 짧은 시간에 요청을 한꺼번에 보내는 부하 형태. 이 테스트에서는 각 단계의 VU가 답안을 한 번씩 동시에 제출한다.                                                 |
| Stage             | 하나의 동시 제출 규모를 실행하고 판정하는 단위. `10`, `100`, `200`, `300` 네 단계가 있다.                                                         |
| Baseline          | 답안 제출 부하를 주기 전에 측정한 probe의 기준 성공률과 응답 시간. 부하 중 결과와 비교한다.                                                                |
| Probe             | 답안 제출 부하와 동시에 반복 호출하는 `GET /api/v1/question-types` 요청. 답안 제출과 무관한 일반 API의 성공률과 지연을 측정해 서버 전체가 영향을 받는지 확인한다.             |
| Latency (지연 시간)   | 요청을 보낸 시점부터 응답을 받을 때까지 걸린 시간. 이 문서의 응답 시간과 duration은 이 값을 뜻한다.                                                          |
| p95               | 측정값을 빠른 순서로 정렬했을 때 95%가 이 값 이하라는 뜻. 극단적인 일부 값보다 대부분 사용자가 경험한 상위 지연을 보는 데 사용한다.                                          |
| Threshold         | 테스트 통과 여부를 자동으로 결정하는 기준. 예를 들어 예상 밖 응답 0건, probe 성공률 100%, raw 과부하 503 p95 12초 이내가 있다.                                  |
| 운영 목표 (SLO)       | 서비스가 달성하려는 측정 가능한 목표. 테스트와 모니터링 결과로 달성 여부를 판단하며, 운영 측정 결과와 서비스 요구에 따라 조정할 수 있다.                                         |
| Overload          | active와 bounded queue의 합인 수용 가능량을 넘었거나 queue 대기 제한을 넘은 상태. 둘 다 특정 오류 코드의 503으로 종료한다. HTTP만으로 원인을 구분할 수 없어 서버 queue 지표를 함께 본다. |
| Timeout           | 정해진 시간 안에 응답을 받지 못해 호출자가 기다리기를 중단한 상태. 이 테스트에서는 예상 밖 실패로 분류한다.                                                          |
| Run ID            | 한 번 생성한 fixture 집합을 식별하는 값. 다른 실행의 테스트 데이터를 구분하고 정확히 정리하는 데 사용한다.                                                       |
| Manifest          | run-id와 단계별 `sessionId`, `sessionQuestionId`, `speechAnswerId`를 기록한 JSON 파일. k6 실행과 fixture cleanup이 같은 대상을 사용하도록 연결한다. |
| Cleanup           | manifest에 기록된 테스트 데이터를 DB에서 삭제하는 작업. 테스트 성공·실패와 관계없이 다시 실행할 수 있다.                                                       |

### 동시성 및 서버 자원

| 용어                                | 의미                                                                                                                                                                                    |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Limiter                           | 특정 작업이 동시에 너무 많이 실행되지 않도록 입장 수를 제한하는 장치. 현재 답안 평가 limiter는 OpenAI 평가 구간을 보호한다.                                                                                                        |
| Permit                            | OpenAI 평가를 동시에 실행할 수 있는 논리적인 권한표. 기본 32개다. 완료·실패한 작업은 permit을 정확히 한 번 반환하므로 순간 active는 항상 32 이하여야 한다.                                                                       |
| Bounded queue                     | permit을 즉시 얻지 못한 제출을 최대 64건, 10초 동안 FIFO로 보관한다. `CompletableFuture`만 보관해 request thread, 평가 executor thread, DB connection을 점유하지 않는다. queue full·timeout은 provider 호출 전 503이다.                  |
| Active                            | 현재 사용 중인 자원의 수. OpenAI active는 permit을 점유한 평가 수이고 Hikari active는 사용 중인 DB connection 수이므로 지표의 주체를 함께 봐야 한다.                                                                           |
| HikariCP                          | 애플리케이션의 DB connection pool. 제한된 실제 DB 연결을 빌려주고 트랜잭션이 끝나면 회수한다.                                                                                                                        |
| Hikari active connections         | pool에서 빌려줘 현재 애플리케이션이 점유 중인 DB connection 수. SQL 실행이 끝났어도 열린 트랜잭션이나 OSIV 때문에 반환하지 않았다면 active에 포함된다.                                                                                  |
| Hikari pending                    | DB connection을 빌리려고 기다리는 스레드 수. HTTP 요청 대기열이나 OpenAI 작업 수가 아니며, 지속되면 DB 연결 고갈을 뜻한다.                                                                                                   |
| Hikari max connections            | 애플리케이션 인스턴스 하나의 Hikari pool이 제공할 수 있는 최대 DB connection 수. DB 서버 전체의 최대 연결 수와는 다르다.                                                                                                    |
| Tomcat busy threads               | 현재 HTTP 요청 코드를 실행하거나 자원을 기다리며 점유된 Tomcat 요청 스레드 수. max threads에 도달하면 다른 API를 처리할 스레드가 부족해진다.                                                                                          |
| Tomcat current threads            | Tomcat이 현재 생성해 둔 전체 요청 worker thread 수. busy와 idle thread를 모두 포함하므로 current가 max와 같아도 모두 idle이면 포화가 아니다.                                                                              |
| Tomcat max threads                | Tomcat connector가 생성할 수 있는 요청 worker thread의 설정상 최대 수. 현재 사용자 API용 `http-nio-8080`은 200이다.                                                                                            |
| Async dispatch                    | Tomcat 요청 스레드가 OpenAI 완료를 직접 기다리지 않고 반환된 뒤, 비동기 결과가 준비되면 다시 HTTP 응답 처리를 이어가는 흐름.                                                                                                      |
| OSIV (Open EntityManager in View) | 웹 요청 동안 JPA EntityManager를 유지하는 기능. 편리한 lazy loading을 지원하지만 비동기 요청에서는 DB connection 수명이 요청 완료까지 길어지는 원인이 될 수 있다.                                                                      |
| Preparation limiter               | OpenAI permit을 얻기 전 DB 조회·제출 예약 같은 준비 구간의 동시 진입도 제한하는 장치. 현재는 구현하지 않았으며 OSIV 같은 자원 수명 문제를 제외한 뒤에도 준비 구간 병목이 재현될 때만 검토한다.                                                              |
| Recovery                          | 한 단계가 끝난 뒤 OpenAI active, queue size, Hikari pending이 10초 연속 0인지 확인하는 과정. permit, 대기 항목, DB connection이 정상 반환됐음을 확인한다.                                                                |

### 관측 및 실행 환경

| 용어            | 의미                                                                                                                 |
| ------------- | ------------------------------------------------------------------------------------------------------------------ |
| Fake OpenAI   | OpenAI 호환 응답을 일정 시간 지연해 반환하는 로컬 서버. 네트워크 비용과 답변 품질을 제외하고 애플리케이션의 외부 API 대기 동작을 반복 가능하게 만든다.                        |
| Actuator      | 실행 중인 Spring Boot 애플리케이션의 health와 서버·JVM 지표를 관리 포트로 노출하는 기능.                                                       |
| Micrometer    | Tomcat, Hikari, JVM과 애플리케이션의 limiter 값을 공통 지표 형태로 기록하는 계측 라이브러리.                                                   |
| Prometheus 형식 | Micrometer 지표를 수집 도구가 읽을 수 있는 텍스트 형식으로 노출한 것. 이 테스트는 `/actuator/prometheus`를 1초마다 저장한다.                            |
| Docker stats  | AWS 컨테이너의 CPU·메모리 사용량 snapshot. 애플리케이션 내부 병목과 EC2·컨테이너 자원 부족을 구분하는 보조 자료다.                                         |
| API quota     | 외부 API provider가 계정이나 프로젝트에 허용한 사용 한도. 분당 요청·토큰 같은 rate limit이나 일정 기간의 사용 예산을 포함할 수 있으며, 재시도한 호출도 별도로 quota를 소비한다. |
| TCP backlog   | 서버가 아직 `accept`하지 못한 새 TCP 연결을 운영체제가 임시 보관하는 대기열. Tomcat thread나 애플리케이션 작업 대기열과는 별개이며 너무 작으면 burst 중 연결이 거부될 수 있다. |
| Smoke test    | 전체 경우를 깊게 검증하기보다 애플리케이션 기동과 주요 API가 기본적으로 동작하는지 빠르게 확인하는 테스트.                                                      |

## 테스트 목적

답안 제출은 DB에서 제출 대상을 준비한 뒤 OpenAI 평가를 기다리고, 완료 결과를
다시 DB에 기록하는 비동기 요청이다. 외부 API 대기를 비동기로 바꾼 것만으로는
서버가 다수 사용자를 안전하게 처리한다고 단정할 수 없다. 비동기 요청의 앞뒤
DB 작업, JPA 자원 수명, Tomcat dispatch와 외부 호출 제한이 함께 동작해야 한다.

이 테스트는 다음 질문에 답하기 위한 것이다.

* OpenAI가 느려도 요청 스레드와 DB 연결이 대기 시간 내내 점유되지 않는가?
* OpenAI 평가는 동시에 32건까지만 실행되고, 다음 64건은 비차단 FIFO queue가
  흡수하며, queue full·timeout은 provider 호출 전 명시적인 503으로 끝나는가?

* 10·100·200·300명이 동시에 제출해도 사용자별 응답과 DB 대상이 섞이지
  않는가?

* 답안 제출 부하가 관계없는 일반 API까지 지연시키거나 실패시키지 않는가?
* 단계 종료 후 OpenAI permit, queue 항목과 DB connection이 모두 반환되는가?
* 문제가 발생했을 때 OpenAI, DB, Tomcat 중 어느 구간이 먼저 병목이 되는가?

이 테스트는 OpenAI 답변 품질, STT 업로드, 장시간 지속 부하의 최대 TPS를
측정하지 않는다. 로컬 가짜 OpenAI 결과는 애플리케이션의 동시성·자원 격리를
검증하는 값이며 실제 OpenAI와 운영 DB의 성능을 대신하지 않는다.

## 테스트 요청과 실행 모델

각 단계는 같은 문제와 답안 텍스트를 사용하지만 학습 세션,
`LearningSessionQuestion`, 완료된 `SpeechAnswer`는 사용자마다 분리한다. 따라서
응답의 `sessionQuestionId`를 fixture와 비교해 다른 사용자의 결과가 섞였는지
검증할 수 있다.

AWS·Supabase fixture는 다음 전용 계정과 자녀 프로필을 생성하거나 재사용한다.

| 항목 | 값 |
| --- | --- |
| 계정 이메일 | `loadtest-answer@malhaebom.invalid` |
| 자녀 프로필 닉네임 | `load-test` |
| 현재 AWS·Supabase `userId` | `9` |
| 현재 AWS·Supabase `profileId` | `12` |

ID는 현재 운영 테스트 DB에서 확인한 참고값이며 환경이나 데이터 재생성에 따라
달라질 수 있다. fixture 도구는 이 값을 하드코딩하지 않고 이메일과 닉네임으로
대상을 찾는다. cleanup은 실행별 세션·답안 fixture를 삭제하지만, 다음 테스트에서
재사용할 수 있도록 전용 계정과 자녀 프로필은 유지한다.

```text
답안 제출 burst
  → 제출 대상 조회·처리 예약
  → OpenAI permit 즉시 획득 또는 비차단 FIFO queue 대기
  → OpenAI 평가 대기
  → 성공·실패 결과 기록

동시에 probe 20회/초
  → GET /api/v1/question-types
  → 답안 제출과 무관한 API의 가용성 측정
```

* 단계별 동시 제출 수는 `10 → 100 → 200 → 300`이고 각 VU는 한 번만
  제출한다.

* 테스트 시작 후 0–10초에는 답안 제출 없이 probe만 초당 20회 보내 약 200개의
  baseline 표본을 수집한다. 2초 뒤인 12–47초에는 probe를 같은 속도로 35초간
  보내 약 700개의 비교 표본을 수집하며, 답안 제출 burst는 이 구간이 시작된
  2초 뒤인 14초에 실행한다.

* 로컬 가짜 OpenAI는 기본 5초 후 응답해 외부 API 대기 상태를 고정한다.
* 자동 재시도는 사용하지 않는다. 답안 제출 한 건과 OpenAI 호출 횟수를
  대응시켜 최초 호출 실패가 재시도 성공으로 가려지거나, 토큰 과금과 API
  quota 사용량이 증가하는 것을 막기 위함이다.

* 단계 사이에는 OpenAI active, queue size, Hikari pending이 10초 연속 0인지
  확인한다.

## 테스트 코드 실행 순서

### 전체 실행 순서

1. 로컬에서는 5초 지연 Fake OpenAI를 먼저 실행한다. AWS에서는 실제 OpenAI를
   사용하므로 이 과정이 없다.

2. 부하 테스트 설정과 DB로 백엔드를 실행하고 8080 사용자 API와 9090 관리
   endpoint가 준비됐는지 확인한다.

3. `loadTestFixtures`의 `seed` 작업으로 `10 + 100 + 200 + 300 = 610`개의
   독립 fixture를 생성하고 manifest를 저장한다. fixture 도구와 백엔드는 같은
   DB를 사용해야 한다.

4. `run-stages.ps1`이 manifest를 읽어 10, 100, 200, 300 단계를 순서대로
   실행한다.

5. 각 단계가 끝나면 OpenAI active, queue size, Hikari pending이 10초 연속
   0이 될 때까지 기다린 후 다음 단계로 이동한다.

6. 성공·실패와 관계없이 `loadTestFixtures`의 `cleanup` 작업을 manifest로
   실행해 fixture를 삭제한다.

7. 네 단계의 원시 결과로 `build-report.ps1`을 실행해 결과 표와 자동 판정을
   생성한다.

8. 백엔드, Fake OpenAI와 SSH tunnel 같은 테스트용 프로세스를 종료한다.

`run-stages.ps1`은 fixture cleanup을 대신 실행하지 않는다. 중간 단계에서
실패하더라도 manifest는 남으므로 같은 manifest를 사용해 cleanup을 다시 실행해야
한다.

### 단계 하나의 내부 순서

`run-stages.ps1`은 각 단계마다 다음 순서를 반복한다.

1. `stage-{N}` 결과 디렉터리를 만들고 같은 단계의 이전 생성 파일을 정리한다.

2. `collect-metrics.ps1`을 별도 프로세스로 시작한다. 이때부터
   `/actuator/prometheus`를 1초마다 저장하고, AWS에서는 Docker 지표도 함께
   저장한다.

3. k6가 `probe_baseline`, `probe_under_load`, `answer_burst` 세 시나리오를 아래
   시간표에 맞춰 실행한다.

```text
Stage start

0s                         10s  12s  14s                           47s
| baseline probes           |    |    |                              |
+-- 20 req/s, ~200 probes --+    |    |                              |
|                                +-- 20 req/s, ~700 load probes -----+
|                                     +-- N answer submissions ------+
```

위 시간표는 재시도 0회의 기본 실행이다. 재시도 모드에서는 각 raw 503의 최대
queue wait 10초와 응답 여유 2초, jitter 최대 대기를 반영해 answer와 부하 중
probe 시나리오를 자동으로 연장한다.

4. 답안 제출 VU는 manifest에서 자기 fixture를 하나 선택하고 실제 답안 제출
   endpoint를 호출한다. 클라이언트 재시도 모드에서는 정상 과부하 503에만
   같은 `speechAnswerId`로 최대 1~2회 다시 호출한다.

5. k6는 응답을 200 성공, 정상 과부하 503 또는 예상 밖 응답 중 하나로 분류한다.
   200 응답은 `sessionQuestionId`가 해당 VU fixture와 일치하는지도 확인한다.

6. k6가 `summary.json`과 `k6-raw.json`을 기록하고 정적 threshold 결과를
   반환한다.

7. `run-stages.ps1`이 `max(baseline p95 × 2, 1초)`로 해당 단계의 probe p95
   허용값을 계산해 추가 판정한다.

8. OpenAI active, queue size, Hikari pending이 10초 연속 0인지 확인한다. 해당
   단계의 k6 실행 시작 시점부터 기본 300초 안에 복구되지 않으면 다음 단계로
   진행하지 않는다.

9. 지표 수집기를 종료하고 `stage-evaluation.json`과 `k6-exit-code.txt`를
   기록한다.

10. threshold가 실패했지만 자원이 복구됐다면 실패 단계를 기록하고 다음 단계도
    실행한다. 자원 복구가 timeout되면 즉시 전체 실행을 중단한다.

11. 네 단계를 모두 실행한 뒤 실패 단계가 하나라도 있으면
    `run-stages.ps1`이 최종 실패를 반환한다.

### 답안 제출 VU 하나의 분류 순서

```text
answer_attempts 증가
  → 자기 fixture로 POST /answers
  ├─ 200
  │   → answer_classified, answer_success 증가
  │   → 재시도 후 성공이면 answer_retry_recovered 증가
  │   → sessionQuestionId 불일치 시 answer_response_mismatch 증가
  ├─ 503 + ANSWER_ASSESSMENT_OVERLOADED
  │   → answer_raw_expected_overload 증가
  │   ├─ 재시도 잔여 횟수 있음
  │   │   → jitter 대기 후 answer_attempts, answer_retry_attempts 증가
  │   │   → 같은 fixture로 다시 POST
  │   └─ 재시도 소진
  │       → answer_classified, answer_expected_overload 증가
  └─ 그 외 상태·timeout·연결 실패
      → answer_classified, answer_unexpected_response 증가
```

재시도가 없으면 `answer_attempts = answer_classified`다. 재시도 모드에서는
`answer_attempts = answer_classified + answer_retry_attempts`이며,
`answer_classified = answer_success + answer_expected_overload +
answer_unexpected_response`는 항상 사용자 기준 논리적 제출 수와 같아야 한다.
`answer_raw_expected_overload`는 재시도 중간 응답을 포함한 모든 정상 503이고,
`answer_expected_overload`는 재시도를 소진한 최종 503만 센다. 정상 집계에서는
`answer_raw_expected_overload`가 `answer_retry_attempts`와
`answer_expected_overload`의 합과 같아야 한다.
`answer_response_mismatch`는 200 성공 응답에 추가로 표시되는 별도 오류 지표다.

## 측정 항목과 의미

### HTTP 계약과 사용자 격리

| 측정 항목                        | 의미                                                                                       |
| ---------------------------- | ---------------------------------------------------------------------------------------- |
| `answer_attempts`            | 재시도를 포함해 실제로 전송한 답안 제출 HTTP 요청 수                                                        |
| `answer_retry_attempts`      | 최초 요청 이후 추가로 전송한 HTTP 요청 수                                                               |
| `answer_retry_recovered`     | 한 번 이상 정상 503을 받은 뒤 최종 200으로 회복한 논리적 제출 수                                                |
| `answer_classified`          | 최종 200, 최종 503 또는 예상 밖 응답으로 분류를 마친 논리적 제출 수. 동시 제출 수보다 작으면 timeout이나 누락이 있음             |
| `answer_success`             | 최종 HTTP 200인 논리적 제출 수. active 제한 이하 단계에서는 전부 성공하고 100단계에서는 과반이어야 함                         |
| `answer_expected_overload`   | 재시도 없음 또는 재시도 소진 뒤에도 `ANSWER_ASSESSMENT_OVERLOADED`인 최종 제출 수                               |
| `answer_raw_expected_overload` | 재시도 중간 응답을 포함한 모든 `ANSWER_ASSESSMENT_OVERLOADED` HTTP 응답 수                                    |
| `answer_unexpected_response` | 그 외 상태와 timeout·연결 실패 수. 항상 0이어야 함                                                       |
| `answer_response_mismatch`   | 200 응답을 파싱할 수 없거나 `sessionQuestionId`가 해당 VU fixture와 다른 수. 응답 계약 위반·혼합을 의미하므로 항상 0이어야 함 |

성공 수가 동시 제출 수보다 작다는 사실만으로 실패라고 판정하지 않는다. 기본
active 32 + queue 64 = 96건을 즉시 수용할 수 있으므로 100단계는 queue 경계에서
대부분 흡수되는지를 과반 성공으로 판정하며 100% 성공을 hard threshold로 두지
않는다. 수용량을 충분히 넘는 200·300단계에서는 성공과 raw 정상 과부하 503이
모두 관찰되어야 한다. 재시도 후 모든 논리적 제출이 성공할 수도 있으므로 retry
모드의 최종 503은 필수 관찰값이 아니다. 어느 순간에도 active는 32, queue size는
64를 넘으면 안 된다. 최종 200, 최종 503, 예상 밖 응답의 합은 논리적 제출 수와
같아야 하며 누락은 없어야 한다. raw 503은 HTTP 응답 수이지 provider 호출 수가
아니다.

### 응답 시간과 probe

| 측정 항목                                     | 의미                                                                   |
| ----------------------------------------- | -------------------------------------------------------------------- |
| `answer_success_duration`                 | 성공한 답안 제출의 전체 HTTP 처리 시간. 서버 비동기 요청의 hard deadline에 따라 최대 30초 이내여야 함 |
| `answer_overload_duration`                | raw 정상 과부하 503 하나를 반환하는 시간. p95는 최대 queue wait 10초 + 응답 여유 2초인 12초 이내여야 함 |
| `answer_final_success_duration`           | 최초 요청부터 jitter 대기와 재시도를 포함해 최종 200까지 걸린 사용자 체감 시간                            |
| `answer_final_overload_duration`          | 최초 요청부터 재시도를 모두 소진하고 최종 503을 받을 때까지의 사용자 체감 시간                              |
| `probe_baseline_duration`                 | 답안 부하가 없을 때 일반 API의 기준 응답 시간                                         |
| `probe_duration`                          | 답안 부하 중 일반 API의 응답 시간                                                |
| `probe_baseline_success`, `probe_success` | probe HTTP 200 비율. 둘 다 100%여야 함                                      |

probe p95 허용값은 `max(baseline p95 × 2, 1초)`다. baseline 자체가 매우
짧을 때 작은 측정 오차로 실패하지 않게 1초의 하한을 두되, 다른 API가 실제로
느려지거나 실패하는 상황은 감지한다.

로컬 테스트의 timeout은 안쪽 작업이 바깥쪽 요청보다 먼저 종료되도록 다음처럼
계층화한다.

```text
OpenAI 호출 timeout 20초
  < 답안 처리 timeout 25초
  < 서버 비동기 요청 timeout 30초
  < k6 HTTP timeout 35초
```

성공 응답 최대 30초는 `malhaebom.answer-submission.async.request-timeout`에서
도출된 필수 계약이다. raw 과부하 503 p95 12초는 HTTP 응답만으로 즉시 queue
full과 10초 queue timeout을 구분할 수 없기 때문에 두 경로를 모두 수용하는
기준이다. 서버의 `queue.full`, `queue.timeout`, `queue.cancelled` 카운터로 원인을
분리한다. queue 대기는 제출 시작 기준 답안 처리 25초 deadline 안에 포함되며
deadline을 연장하지 않는다.

### 서버 자원과 외부 호출 제한

| 측정 항목                                     | 의미                                                                            |
| ----------------------------------------- | ----------------------------------------------------------------------------- |
| Tomcat busy / current / max threads       | HTTP 요청을 처리 중인 스레드 수와 한도. OpenAI 대기 중 busy가 내려가지 않으면 비동기 처리 앞뒤에서 요청 스레드가 막힌 것 |
| Hikari active / pending / max connections | 사용 중인 DB 연결, 연결을 기다리는 스레드, 풀 한도. pending 지속은 DB 연결 고갈을 의미                     |
| HTTP 상태별 처리 시간                            | 어떤 API와 상태 코드가 느려졌는지 확인하는 Actuator 원시 지표                                      |
| JVM CPU·메모리·GC                            | 스레드·DB 병목과 별개로 JVM 계산량이나 GC 정지가 원인인지 확인하는 보조 지표                               |
| 컨테이너 CPU·메모리                              | EC2 컨테이너 한도 또는 호스트 자원 부족 여부를 확인하는 보조 지표                                       |
| OpenAI active / queue size                     | 실제 provider 평가 수와 FIFO 대기 수. 각각 설정값 32와 64 이하여야 함                                  |
| queue full / timeout / cancelled               | provider 호출 전 종료 원인을 구분하는 서버 누적 카운터. 단계별 첫·마지막 snapshot 차이로 집계함                  |

Hikari `최대 pending`은 특정 1초 표본에서 기다린 스레드 수이고,
`pending 지속 시간`은 pending이 0보다 큰 상태가 연속된 시간이다. 순간적인
pending이 발생해도 2초 전에 해소되고 단계 종료 후 0으로 복구되면 이 테스트의
실패 기준에는 해당하지 않는다.

OpenAI 답안 평가 limiter가 제공하는 Micrometer 지표의 이름과 의미는
[답안 평가 동시성 제한 지표](../../docs/answer-assessment-metrics.md)를 참고한다.

`active`가 32 이하라는 사실만으로 테스트가 성공한 것은 아니다. Hikari와
Tomcat이 먼저 포화되면 요청이 limiter에 도달하지 못해 active가 낮게 보일 수
있다. 따라서 limiter, DB, Tomcat과 probe를 함께 해석해야 한다.

## 결과 해석

| 관측 조합                                                     | 해석                                       |
| --------------------------------------------------------- | ---------------------------------------- |
| active가 32, queue가 64 경계에 도달하고 바깥 요청이 정상 503이며 probe가 안정적 | limiter, bounded queue와 서버 자원 격리가 의도대로 동작 |
| active가 32보다 낮은데 Hikari pending이 지속되고 Tomcat·probe가 악화    | limiter 앞의 DB 준비 또는 JPA 자원 수명이 우선 병목     |
| Hikari는 안정적이지만 Tomcat busy가 OpenAI 대기 중 내려가지 않음           | 비동기 dispatch 또는 요청 스레드 반환 경로 점검 필요       |
| active가 32이고 서버 자원은 안정적이지만 성공 응답만 느림                      | 실제 provider 지연 또는 답안 평가 timeout 정책 점검 필요 |
| 단계 종료 후 active, queue, pending 중 하나가 0으로 돌아오지 않음          | permit, queue 항목 또는 DB connection 누수 가능성       |

자동 판정 기준은 다음과 같다.

* 예상하지 못한 상태, 응답 누락, 응답 혼합은 0건이다.
* OpenAI active는 항상 32 이하, queue size는 64 이하이다.
* 100단계는 과반 성공으로 queue 경계를 대부분 흡수하고, 수용량 96을 충분히 넘는
  200·300단계에서는 raw 정상 과부하 503이 관찰된다.

* 성공 응답은 30초 이내이고 raw 정상 과부하 503 p95는 12초 이내다.
* probe 성공률은 100%이고 p95는 허용값 이하다.
* Tomcat max thread 포화와 Hikari pending이 2초 이상 지속되지 않는다.
* OpenAI 대기 중 Tomcat busy가 max의 25% 아래로 내려간 표본이 존재한다.
* 단계 종료 후 OpenAI active, queue size, Hikari pending이 모두 0으로 복구된다.

각 기준의 성격과 숫자의 근거는 다음과 같다.

| 판정 기준                                | 성격               | 근거와 의미                                                                                              |
| ------------------------------------ | ---------------- | --------------------------------------------------------------------------------------------------- |
| 예상 밖 상태·누락·응답 혼합 0건                  | 동작 계약            | 하나라도 발생하면 HTTP 계약 또는 사용자 격리가 깨진 것이므로 허용하지 않는다.                                                      |
| OpenAI active 32·queue size 64 이하      | 설정 불변식           | limiter와 bounded queue가 각각 설정 경계를 항상 지켜야 한다.                                                     |
| 100단계 과반 성공                        | queue 경계 관찰       | active + queue 96건이 짧은 burst를 대부분 흡수하는지 확인하되 100% 성공을 강제하지 않는다.                               |
| 200·300단계에서 정상 503 관찰              | 동작 계약            | 수용량을 충분히 넘을 때 queue 바깥 요청이 provider까지 전달되지 않고 명시적으로 거절되는지 증명한다.                                |
| 성공 응답 최대 30초                         | hard deadline    | 서버 비동기 요청 timeout 30초에서 도출한다. 성공한 모든 요청에 적용하므로 p95가 아니라 최대값을 사용한다.                                  |
| raw 과부하 503 p95 12초                 | queue 계약 + 응답 여유 | HTTP로 구분할 수 없는 queue full과 최대 10초 timeout을 함께 판정하기 위해 응답 여유 2초를 더한다. 서버 카운터로 원인을 분리한다.                |
| probe 성공률 100%                       | 통제된 부하 테스트 합격 기준 | 점검 시간의 격리된 테스트에서는 답안 제출 때문에 관계없는 API가 한 건이라도 실패하는 것을 허용하지 않는다. 일반 운영 전체의 가용성 SLO를 100%로 선언한 것은 아니다. |
| probe p95 `max(baseline × 2, 1초)` 이하 | 상대적 초기 운영 목표     | 환경별 기본 지연을 반영하고 매우 짧은 baseline의 측정 오차에는 1초 하한을 적용한다. AWS 결과 후 조정할 수 있다.                             |
| Hikari pending 지속 2초 미만              | 진단 기준            | 순간적인 connection 경쟁과 지속적인 pool 고갈을 구분하기 위한 값이다. Hikari 설정에서 자동으로 도출된 값은 아니다. AWS 결과 후 조정할 수 있다.      |
| Tomcat max thread 포화 2초 미만           | 진단 기준            | 순간 burst와 다른 API까지 막는 지속 포화를 구분한다. Tomcat 설정에서 자동으로 도출된 값은 아니다. AWS 결과 후 조정할 수 있다.                  |
| OpenAI 대기 중 Tomcat busy 25% 미만 관찰    | 비동기 구조 검증 기준     | provider 대기 동안 요청 스레드가 반환되는지 확인한다. max 200개 기준으로 busy가 50개 아래로 내려간 표본이 있어야 한다.                      |
| 종료 후 active·queue·pending 10초 연속 0   | 자원 복구 계약         | permit, queue 항목과 DB connection이 모두 반환돼야 한다. 10초는 일시적인 완료 순서 차이를 흡수하는 안정화 구간이다.                         |

JVM CPU·메모리·GC와 컨테이너 CPU·메모리는 현재 자동 합격선을 두지 않은 진단
지표다. HTTP 판정이 실패하거나 지연이 커졌을 때 애플리케이션 내부 병목과
호스트 자원 부족을 구분하는 근거로 사용하며, AWS 측정값이 쌓인 뒤 별도의 운영
목표를 정한다.

준비 구간 limiter는 위 지표에서 OSIV 같은 자원 수명 문제를 먼저 제외한 뒤에도
Tomcat 또는 Hikari 포화와 probe 저하가 재현될 때만 검토한다.

bounded queue는 짧은 burst의 사용자 체감 성공률을 높일 뿐 provider 처리량을
늘리지 않는다. 따라서 300단계 전부 성공을 보장하지 않으며, queue 대기를 이유로
제출 시작 기준 25초 답안 처리 deadline을 연장하지도 않는다.

## 생성되는 결과 파일

| 파일                                                 | 용도                                     |
| -------------------------------------------------- | -------------------------------------- |
| `fixture-manifest.json`                            | run-id와 단계별 독립 fixture ID. 재실행과 정리의 기준 |
| `stage-{N}/summary.json`                           | k6 집계 지표와 threshold 결과                 |
| `stage-{N}/k6-raw.json`                            | 요청별 k6 원시 시계열                          |
| `stage-{N}/stage-evaluation.json`                  | probe 허용값, collector 종료 상태, 자원 회복 결과   |
| `stage-{N}/server-metrics/actuator-prometheus.txt` | 1초 간격 Actuator·Prometheus 전체 snapshot  |
| `stage-{N}/server-metrics/docker-stats.jsonl`      | AWS 실행 시 컨테이너 CPU·메모리 snapshot         |
| `stage-{N}/server-metrics/backend-container.log`   | AWS 실행 단계 동안의 백엔드 컨테이너 로그              |

자동 보고서는 주요 판정 지표만 추려 표로 만든다. JVM, HTTP 상태별 처리 시간과
누적 limiter counter 같은 세부 원인은 `actuator-prometheus.txt` 원본에서 추가로
확인한다.

## 사전 요구 사항

* PowerShell 7 (`pwsh`). Windows와 macOS에서 같은 `.ps1`을 실행한다.
* Java 21과 Gradle Wrapper
* k6
* Python 3
* AWS 실행 시 EC2의 Docker Compose, 로컬 OpenSSH와 EC2 접속 키

결과와 fixture manifest는 `load-tests/results/`에 생성되며 Git에서 제외된다.
자동화 스크립트는 macOS에서 Gradle Wrapper를 `bash gradlew`로 실행하므로 실행
권한 비트에 의존하지 않는다. 수동으로 `./gradlew`를 실행하려면 프로젝트 루트에서
한 번 `chmod +x gradlew`를 실행한다. EC2 private key는 OpenSSH가 거부하지 않도록
`chmod 600 <키 경로>`로 제한한다.

## 로컬 실행

아래 명령은 프로젝트 루트의 서로 다른 PowerShell 7 (`pwsh`)에서 실행한다.
PowerShell과 k6, Python은 로컬 Windows 또는 macOS에 설치하며 EC2에는 설치하지
않는다. DB 파일 이름은 실행마다 새 값으로 바꿔 이전 결과와 격리한다.

기본값은 active 32, queue capacity 64, max queue wait 10초다. 다른 설정을
계측할 때는 백엔드의 `ANSWER_ASSESSMENT_MAX_CONCURRENT_REQUESTS`,
`ANSWER_ASSESSMENT_QUEUE_CAPACITY`, `ANSWER_ASSESSMENT_MAX_QUEUE_WAIT`와 두
PowerShell 스크립트의 `-AssessmentLimit`, `-AssessmentQueueCapacity`,
`-AssessmentMaxQueueWaitSeconds`를 각각 같은 값으로 지정한다.

클라이언트 재시도 모드는 기본적으로 꺼져 있다. `-ClientMaxRetries 1` 또는
`2`로 활성화하며 프런트 정책과 같은 간격을 사용한다.

| 재시도 | 정상 과부하 503 뒤의 jitter 대기 |
| ---: | --- |
| 1차 | 1~2초 |
| 2차 | 3~5초 |

재시도 없음과 재시도 1·2회를 비교할 때는 각 실행마다 새 fixture run-id, DB 파일과
결과 디렉터리를 사용한다. 앞선 실행에서 최종 성공한 `speechAnswerId`를 다시
제출하면 기존 완료 결과가 반환되므로 같은 fixture를 A/B 비교에 재사용하지 않는다.

```powershell
$pythonCommand = if (Get-Command python3 -ErrorAction SilentlyContinue) {
  'python3'
} else {
  'python'
}
& $pythonCommand load-tests/answer-submission/fake-openai.py --delay-seconds 5
```

가짜 서버는 300건 burst가 운영체제의 작은 기본 TCP backlog에서 거절되지 않도록
1024개의 대기 연결을 허용한다.

백엔드와 fixture 도구가 같은 파일 H2를 사용하도록 환경을 설정한다.

```powershell
$gradleWrapper = if ($IsWindows) { './gradlew.bat' } else { './gradlew' }
# JAVA_HOME이 이미 Java 21이면 설정하지 않아도 된다.
# $env:JAVA_HOME = '<OS에 맞는 JDK 21 home>'
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:file:./.private/load-tests/answer-load-20260821;MODE=PostgreSQL;AUTO_SERVER=TRUE'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'update'
$env:SPRING_AI_OPENAI_API_KEY = 'load-test-local'
$env:SPRING_AI_OPENAI_BASE_URL = 'http://127.0.0.1:18080'
$env:SPRING_AI_OPENAI_TIMEOUT = '20s'
$env:SPRING_AI_OPENAI_MAX_RETRIES = '0'
$env:ANSWER_ASSESSMENT_MAX_CONCURRENT_REQUESTS = '32'
$env:ANSWER_ASSESSMENT_QUEUE_CAPACITY = '64'
$env:ANSWER_ASSESSMENT_MAX_QUEUE_WAIT = '10s'
$env:GCP_STT_ENABLED = 'false'
New-Item -ItemType Directory -Force load-tests/results/local | Out-Null
& $gradleWrapper loadTestServer --no-daemon 2>&1 `
  | Tee-Object -FilePath load-tests/results/local/backend.log
```

백엔드가 준비된 후 fixture 610건을 만든다. 같은 datasource 환경변수를 설정한
PowerShell에서 실행해야 한다.

```powershell
$gradleWrapper = if ($IsWindows) { './gradlew.bat' } else { './gradlew' }
& $gradleWrapper loadTestFixtures --no-daemon `
  -PloadTestAction=seed `
  -PloadTestRunId=local-20260821 `
  -PloadTestManifest=load-tests/results/local/fixture-manifest.json
```

네 단계를 실행한다.

```powershell
./load-tests/answer-submission/run-stages.ps1 `
  -Manifest load-tests/results/local/fixture-manifest.json `
  -ResultRoot load-tests/results/local `
  -AssessmentLimit 32 `
  -AssessmentQueueCapacity 64 `
  -AssessmentMaxQueueWaitSeconds 10 `
  -ClientMaxRetries 0
```

프런트 후보 정책인 최대 2회 재시도는 별도 fixture와 결과 경로에서 실행한다.

```powershell
./load-tests/answer-submission/run-stages.ps1 `
  -Manifest load-tests/results/local-retry2/fixture-manifest.json `
  -ResultRoot load-tests/results/local-retry2 `
  -AssessmentLimit 32 `
  -AssessmentQueueCapacity 64 `
  -AssessmentMaxQueueWaitSeconds 10 `
  -ClientMaxRetries 2
```

각 단계는 부하 전 probe와 부하 중 probe를 분리해 기록한다. 단계 종료 후
OpenAI active, queue size, Hikari pending이 10초 연속 0이 될 때까지 기다리며,
기본 300초 안에 회복하지 않으면 다음 단계로 넘어가지 않는다. probe 지연 판정은
`max(baseline p95 × 2, 1초)`를 사용한다. 같은 결과 디렉터리에 다시 실행하면
해당 단계에서 생성한 파일만 새 결과로 교체된다.

가짜 OpenAI의 최대 동시 요청은 다음 주소에서도 확인할 수 있다.

```powershell
Invoke-RestMethod http://127.0.0.1:18080/metrics
```

종료 전 fixture를 정리한다.

```powershell
$gradleWrapper = if ($IsWindows) { './gradlew.bat' } else { './gradlew' }
& $gradleWrapper loadTestFixtures --no-daemon `
  -PloadTestAction=cleanup `
  -PloadTestManifest=load-tests/results/local/fixture-manifest.json
```

## OSIV 설정

공통 설정은 `spring.jpa.open-in-view=false`를 사용한다. 트랜잭션이 끝난 뒤에도
웹 요청과 비동기 dispatch 수명까지 JPA 자원이 유지되어 DB connection 반환이
늦어지는 것을 막기 위한 설정이며 local, prod와 test 프로필에 동일하게 적용된다.

부하 테스트를 실행할 때 `SPRING_JPA_OPEN_IN_VIEW=true`로 덮어쓰지 않는다.
OSIV 비활성화로 lazy loading에 의존하던 조회 API가 영향을 받지 않는지는 일반
테스트와 별도 smoke test로 확인한다.

## AWS 실행

### 한 명령 자동 실행

`invoke-aws-load-test.ps1`은 원격 loadtest Compose 기동, SSH tunnel, readiness
확인, 시나리오별 fixture seed·k6·cleanup과 운영 Compose 복구를 순서대로 실행한다.
한 시나리오의 k6 threshold가 실패해도 나머지 시나리오를 계속 실행하고, 마지막에
실패 목록을 반환한다. fixture cleanup이나 기반 환경이 실패하면 추가 실행을 중단하고
`finally`에서 남은 fixture와 원격 Compose 복구를 시도한다. 설정 파일의 limiter
32/64/10 값은 원격 Compose와 모든 k6 시나리오에 동일하게 전달되고 OpenAI SDK 자동
재시도는 원격 WAS에서 0회로 고정된다.

Compose 전환은 EC2에서 각각 `start-loadtest-compose.sh`와
`restore-prod-compose.sh`를 실행한다. 자동 실행에서는 loadtest Compose를 한 번만
시작하고 설정 파일의 `Scenarios`를 모두 실행한 뒤 `finally`에서 운영 Compose로
복구한다.

최초 한 번 추적되지 않는 `.private` 설정 파일을 만든다.

```powershell
New-Item -ItemType Directory -Force .private/load-tests | Out-Null
Copy-Item `
  load-tests/answer-submission/aws-load-test.example.psd1 `
  .private/load-tests/aws-load-test.psd1
```

복사한 파일에서 다음 세 값을 실제 환경에 맞게 확인·지정한다.

* `BaseUrl`: 공개 백엔드 URL
* `SshHost`: `user@host` 형식의 EC2 SSH 대상
* `SshIdentityFile`: 로컬 EC2 private key 절대 경로

`RemoteProjectDirectory`는 현재 self-hosted runner checkout 경로인
`/home/ubuntu/actions-runner/_work/backend/backend`가 예제에 반영되어 있다. runner
구성이 바뀐 경우에만 새 EC2 절대 경로로 수정한다.

`JAVA_HOME`이 JDK 21이 아니면 같은 설정 파일의 `JavaHome`도 지정한다. API key는
이 파일에 넣지 않으며 기존 `config/application.yaml` 설정을 재사용한다.

`Scenarios`의 각 항목은 고유한 소문자·숫자·하이픈 이름과 동시성 단계, 클라이언트
재시도 횟수를 가진다. 다음 예시는 같은 서버 limiter에서 재시도 0·1·2회를 비교한다.

```powershell
Scenarios = @(
  @{ Name = "baseline-retry0"; Stages = @(10, 100, 200, 300); ClientMaxRetries = 0 }
  @{ Name = "retry1"; Stages = @(10, 100, 200, 300); ClientMaxRetries = 1 }
  @{ Name = "retry2"; Stages = @(10, 100, 200, 300); ClientMaxRetries = 2 }
)
```

각 시나리오는 별도 fixture run-id와 결과 폴더를 사용한다. 최상위 `run-plan.json`에는
전체 test-id, 설정, 시나리오별 단계와 최대 HTTP 시도 횟수가 저장된다.

```text
load-tests/results/aws/<test-id>/
├─ run-plan.json
├─ baseline-retry0/
├─ retry1/
└─ retry2/
```

이후 전체 AWS 테스트는 다음 한 명령으로 실행한다.

```powershell
./load-tests/answer-submission/invoke-aws-load-test.ps1 `
  -ConfirmLiveOpenAiCost
```

비용 확인 스위치가 없으면 실제 OpenAI 부하 테스트를 시작하지 않는다. 시작 전에
모든 시나리오와 합산 최대 HTTP 시도 횟수를 출력한다. 예제의 세 시나리오는 논리
제출 1,830건, 최대 HTTP 시도 3,660회다. 스크립트는 실행별 UTC test-id와 결과 폴더를
자동 생성하고 테스트 중 Grafana 주소를 출력한다. 정상 종료 시 SSH tunnel을 닫고
운영 Compose만 남긴다.

PowerShell 프로세스 강제 종료나 로컬 전원 종료처럼 `finally`가 실행되지 않은
경우에는 아래 수동 절차의 fixture cleanup과 운영 Compose 복구 명령을 실행한다.

### 수동 실행 및 복구

아래 PowerShell 스크립트와 k6는 로컬 Windows 또는 macOS에서 실행한다. EC2는
Linux여도 되며 PowerShell이 필요하지 않다. 평상시 운영 Compose는 Actuator 관리
포트를 host에 공개하지 않는다. 부하 테스트를 시작하기 전에 EC2의 backend checkout
루트에서 loadtest 시작 스크립트를 실행한다. 실행 권한 비트에 의존하지 않도록
`bash`로 호출한다.

```bash
bash load-tests/answer-submission/start-loadtest-compose.sh
```

loadtest override는 Prometheus와 Grafana를 함께 시작한다. 백엔드 관리 포트 9090,
Prometheus 9091, Grafana 3000은 모두 EC2 loopback에만 공개된다. 외부에서는
여전히 닫혀 있어야 하므로 먼저 세 포트에 직접 연결할 수 없는지 확인한다.

Windows에서는 `TcpTestSucceeded`가 `False`인지 확인한다.

```powershell
9090, 9091, 3000 | ForEach-Object {
  Test-NetConnection 3.35.11.125 -Port $_
}
```

macOS에서는 다음 명령의 연결이 실패해야 한다.

```bash
for port in 9090 9091 3000; do nc -vz 3.35.11.125 "$port"; done
```

확인 후 로컬의 별도 terminal에서 SSH tunnel을 연다. 접속 키 경로는 로컬 OS에
맞게 지정한다.

```powershell
$sshKey = '<로컬 EC2 접속 키 경로>'
ssh -N `
  -L 19090:127.0.0.1:9090 `
  -L 19091:127.0.0.1:9091 `
  -L 13000:127.0.0.1:3000 `
  -i $sshKey ubuntu@3.35.11.125
```

Prometheus는 Compose network에서 `was:9090/actuator/prometheus`를 1초마다
수집한다. 로컬 k6는 19091 터널을 통해 결과를 Remote Write하고, Grafana는
<http://127.0.0.1:13000>에서 로그인 없이 읽기 전용으로 확인한다. 대시보드의
`Test run`과 `Stage` 변수는 manifest의 run-id와 동시성 단계를 사용한다.

`SPRING_PROFILES_ACTIVE=prod`와 운영 datasource 설정으로 fixture를 만든 뒤 같은
manifest를 k6에 전달한다. fixture 생성·정리는 반드시 동일한 DB를 사용해야 한다.
테스트 배포 환경에서도 `SPRING_AI_OPENAI_MAX_RETRIES=0`으로 지정해 호출 수와
응답 분류가 자동 재시도의 영향을 받지 않게 한다.

```powershell
$gradleWrapper = if ($IsWindows) { './gradlew.bat' } else { './gradlew' }
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:SPRING_AI_OPENAI_MAX_RETRIES = '0'
$runId = 'aws-' + [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$resultRoot = "load-tests/results/aws/$runId"
$manifest = "$resultRoot/fixture-manifest.json"

& $gradleWrapper loadTestFixtures --no-daemon `
  "-PloadTestAction=seed" `
  "-PloadTestRunId=$runId" `
  "-PloadTestManifest=$manifest"
```

같은 PowerShell에서 생성된 manifest와 결과 경로를 사용해 k6를 실행한다.

```powershell
$sshKey = '<로컬 EC2 접속 키 경로>'
./load-tests/answer-submission/run-stages.ps1 `
  -BaseUrl http://3.35.11.125 `
  -ManagementUrl http://127.0.0.1:19090 `
  -PrometheusRemoteWriteUrl http://127.0.0.1:19091/api/v1/write `
  -Manifest $manifest `
  -ResultRoot $resultRoot `
  -DockerContainer backend-was-1 `
  -SshHost ubuntu@3.35.11.125 `
  -SshIdentityFile $sshKey `
  -AssessmentLimit 32 `
  -AssessmentQueueCapacity 64 `
  -AssessmentMaxQueueWaitSeconds 10 `
  -ClientMaxRetries 0
```

k6 성공·실패와 관계없이 같은 datasource 환경과 manifest로 fixture를 정리한다.

```powershell
& $gradleWrapper loadTestFixtures --no-daemon `
  "-PloadTestAction=cleanup" `
  "-PloadTestManifest=$manifest"
```

queue 자체의 동작을 먼저 보기 위해 재시도 0회를 기준 실행으로 삼고, 최대 2회
재시도는 새 fixture와 결과 경로에서 보조 비교로 실행한다. loadtest Compose를 유지한
상태에서 서로 다른 `Stages`, limiter, 재시도 설정으로 `run-stages.ps1`을 여러 번
호출할 수 있다. 각 실행은 새 fixture와 결과 경로를 사용한다. 실행 중 일반 사용자
요청을 피하고, 모든 시나리오 종료 후에는 fixture cleanup과
`malhaebom.answer.assessment.active=0`,
`malhaebom.answer.assessment.queue.size=0`,
`hikaricp.connections.pending=0`을 확인한다. 실제 OpenAI는 완료된 permit을 뒤
요청이 다시 사용할 수 있으므로 네 단계 합계 최대 610회의 과금 호출이 발생할 수
있다.
최대 2회 재시도에서는 답안 제출 HTTP 요청이 최대 1,830회까지 증가할 수 있지만,
raw 정상 503은 OpenAI 호출 전에 거절되므로 그 자체는 provider 과금 호출이 아니다.
`DockerContainer`를 지정하면 단계별 Docker CPU·메모리와 해당 단계의 컨테이너
로그도 함께 보관된다.

`PrometheusRemoteWriteUrl`을 지정해도 기존 `summary.json`, `k6-raw.json`과
Actuator snapshot은 그대로 생성된다. 자동 실행의 k6 지표에는 전체 실행을 나타내는
`testid`, 설정 항목 이름인 `scenario`, 동시성인 `stage` 태그가 추가된다. Grafana의
`Test run`, `Scenario`, `Stage` 변수로 세 축을 각각 선택할 수 있다.

부하 테스트와 fixture cleanup을 마치면 EC2에서 운영 Compose만 사용해 서비스를
재생성한다. `--remove-orphans`가 Prometheus와 Grafana 컨테이너를 제거하고 host의
9090, 9091, 3000 포트 매핑을 모두 제거한다. Prometheus named volume은 남으므로
다음 loadtest 실행에서도 최대 14일의 이전 시계열을 조회할 수 있다.
수동 정리를 빼먹더라도 다음 CI/CD 운영 배포의 `--remove-orphans`가 loadtest 전용
컨테이너를 제거한다.

```bash
bash load-tests/answer-submission/restore-prod-compose.sh
```

## 결과 보고서 생성

```powershell
./load-tests/answer-submission/build-report.ps1 `
  -ResultRoot load-tests/results/local `
  -OutputPath .private/docs/backend-architecture/answer-submission-load-test.md `
  -Environment local-fake-openai `
  -GitCommit (git rev-parse --short HEAD) `
  -FixtureRunId local-20260821 `
  -AssessmentLimit 32 `
  -AssessmentQueueCapacity 64 `
  -AssessmentMaxQueueWaitSeconds 10 `
  -ClientMaxRetries 0
```

자동 생성된 표에 원시 지표의 지속 시간과 결론을 보완한다. 준비 limiter는
Tomcat 포화, Hikari pending 지속 또는 probe API 지연이 실제로 확인될 때만 다시
검토한다.
