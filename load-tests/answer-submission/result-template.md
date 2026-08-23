# 답안 제출 비동기 부하 테스트 결과

- 실행 일시:
- 실행 환경:
- Git commit:
- fixture run-id:
- OpenAI 대상: fake / live
- `ANSWER_ASSESSMENT_MAX_CONCURRENT_REQUESTS`: 48

| 동시 제출 | 200 성공 | 예상 503 | 기타 오류 | 성공 p95 | 503 p95 | probe p95 | OpenAI 최대 active | Tomcat 최대 busy | Hikari 최대 pending |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 |  |  |  |  |  |  |  |  |  |
| 100 |  |  |  |  |  |  |  |  |  |
| 200 |  |  |  |  |  |  |  |  |  |
| 300 |  |  |  |  |  |  |  |  |  |

## 판정

- 10/100/200/300 네 단계 결과와 누락 응답 0건:
- 예상하지 못한 상태·응답 혼합 0건:
- 각 단계 성공 응답 및 48건 초과 단계의 예상 503 관찰:
- OpenAI active 48 이하:
- probe 성공률 100%, p95 기준 이내:
- provider 대기 중 Tomcat busy가 max의 25% 미만:
- Hikari pending이 2초 이상 지속되지 않음:
- 종료 후 permit·DB connection 복구:

## 결론

- preparation limiter 필요 여부:
- 병목과 후속 작업:
