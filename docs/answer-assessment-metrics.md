# 답안 평가 동시성 제한 지표

`AnswerAssessmentConcurrencyLimiter`는 OpenAI 답안 평가 작업의 동시 실행 수를
제한하고 다음 Micrometer 지표를 제공한다.

| 지표                                      | 종류    | 의미                                                                    |
| ----------------------------------------- | ------- | ----------------------------------------------------------------------- |
| `malhaebom.answer.assessment.active`      | Gauge   | 현재 permit을 점유하고 평가 중인 작업 수                                |
| `malhaebom.answer.assessment.limit`       | Gauge   | 허용된 최대 동시 평가 수. 기본값은 48                                   |
| `malhaebom.answer.assessment.accepted`    | Counter | permit을 획득해 평가를 시작한 누적 수                                   |
| `malhaebom.answer.assessment.rejected`    | Counter | permit을 얻지 못해 OpenAI 호출 전에 과부하로 거절된 누적 수              |
| `malhaebom.answer.assessment.completed`   | Counter | permit을 점유한 비동기 작업이 정상적으로 완료된 누적 수                  |
| `malhaebom.answer.assessment.failed`      | Counter | permit을 획득한 뒤 작업 생성 또는 비동기 완료가 예외로 끝난 누적 수      |

`rejected`는 permit을 획득하지 않은 요청이므로 `failed`에 포함되지 않는다.
permit을 획득한 요청은 최종적으로 `completed` 또는 `failed` 중 하나로 분류되며,
완료 시 permit을 반환하고 `active`에서 제외된다. Counter는 애플리케이션 프로세스가
시작된 뒤의 누적값이고, 프로세스가 재시작되면 초기화된다.

Prometheus로 노출될 때는 Micrometer의 이름 변환 규칙에 따라 점(`.`)이
밑줄(`_`)로 바뀌고 Counter에는 `_total` 접미사가 붙는다. 예를 들어
`malhaebom.answer.assessment.accepted`는
`malhaebom_answer_assessment_accepted_total`로 노출된다.
