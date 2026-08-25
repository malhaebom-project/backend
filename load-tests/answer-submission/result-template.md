# 답안 제출 비동기 부하 테스트 결과

- 실행 일시:
- 실행 환경:
- Git commit:
- fixture run-id:
- OpenAI 대상: fake / live
- `ANSWER_ASSESSMENT_QUEUE_CAPACITY`: 64
- `ANSWER_ASSESSMENT_MAX_QUEUE_WAIT`: 10s
- 클라이언트 최대 재시도: 0 / 1 / 2

| 동시 제출 | HTTP 시도 | 평균 시도 | 재시도 회복 | 최종 200 | 최종 503 | raw 503 | 기타 오류 | 최종 성공 p95 | 최종 503 p95 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 |  |  |  |  |  |  |  |  |  |
| 100 |  |  |  |  |  |  |  |  |  |
| 200 |  |  |  |  |  |  |  |  |  |
| 300 |  |  |  |  |  |  |  |  |  |

| 동시 제출 | 최대 queue | queue full | queue timeout | queue cancelled | 종료 queue·Hikari pending |
| ---: | ---: | ---: | ---: | ---: | :---: |
| 10 |  |  |  |  |  |
| 100 |  |  |  |  |  |
| 200 |  |  |  |  |  |
| 300 |  |  |  |  |  |

## 판정

- 10/100/200/300 네 단계 결과와 누락 응답 0건:
- `최종 200 + 최종 503 + 기타 오류 = 논리적 제출`:
- 예상하지 못한 상태·응답 혼합 0건:
- HTTP 시도 수가 설정한 재시도 상한 이내:
- 재시도 없음 대비 최종 성공 증가·최종 503 감소:
- 100단계에서 성공이 과반으로 queue 경계를 대부분 흡수:
- 200/300단계에서 raw 정상 503 관찰:
- raw 503 p95 12초 이내(최대 queue wait 10초 + 응답 여유 2초):
- queue size 64 이하:
- queue full / timeout / cancelled 서버 카운터 기록:
- probe 성공률 100%, p95 기준 이내:
- provider/queue 대기 중 Tomcat busy가 max의 25% 미만:
- Hikari pending이 2초 이상 지속되지 않음:
- 종료 후 active=0, queue=0, Hikari pending=0:

HTTP 응답만으로 queue full과 queue timeout을 구분할 수 없으므로 서버 카운터로
원인을 분리한다. raw 503은 provider 호출 수가 아니라 HTTP 과부하 응답 수다.

## 결론

- queue/active 설정 조정 필요 여부:
- 병목과 후속 작업:
- queue는 짧은 burst를 흡수하지만 처리량을 늘리거나 300건 성공을 보장하지 않는다.
- queue 대기도 제출 시작 기준 25초 deadline 안에 포함되며 deadline을 연장하지 않는다.
