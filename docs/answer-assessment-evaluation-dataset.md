# OpenAI 답변 평가 데이터셋

## 목적

이 데이터셋은 OpenAI 답변 평가기가 학습자의 답변을 `CORRECT`,
`PARTIALLY_CORRECT`, `INCORRECT`로 얼마나 정확하고 일관되게 분류하는지
검증하기 위한 입력 데이터다.

평가 파일은
`src/test/resources/answer-assessment-eval-dataset-v1.json`이다.

## 평가 범위

- OpenAI에 전달되는 텍스트 답변의 채점 정확도를 평가한다.
- `UNRECOGNIZED`는 STT 및 음성 입력 품질 평가 범위로 분리하고 이
  데이터셋에서는 제외한다.
- 실제 사용자가 제출한 `answers.answer_text`는 포함하지 않는다.
- 기존 `answers.result`는 현재 AI 평가기의 출력이므로 정답 라벨로 사용하지
  않는다.

## Supabase 원본

- 스냅샷 날짜: 2026-08-26
- 문제: `public.questions`
- 허용 답안: `public.question_accepted_answers`
- 대상: `active = true`
- 모집단: 난이도 3개 x 문제 유형 3개 x 주제 3개 x 각 10문제, 총
  270문제
- 표본: 27개 난이도·문제 유형·주제 조합에서 각 1문제, 총 27문제

표본은 다음 고정 seed 규칙으로 선택했다.

```sql
row_number() over (
  partition by difficulty, type, topic
  order by md5(id::text || ':answer-assessment-eval-v1'), id
)
```

각 조합에서 순위가 1인 문제를 선택하므로 같은 Supabase 스냅샷에서는 동일한
표본을 재현할 수 있다.

## 데이터 분포

각 표본 문제에는 다음 세 답변을 기본으로 둔다.

1. Supabase 허용 답안에서 가져온 `CORRECT`
2. 핵심 의미 일부만 충족하는 합성 `PARTIALLY_CORRECT`
3. 명백한 의미 오류 또는 요구 불충족이 있는 합성 `INCORRECT`

오답을 정답으로 승인하는 false positive를 더 강하게 검증하기 위해 각
난이도·문제 유형 조합마다 표면적으로 그럴듯한 오답을 1개씩 추가했다.

| 결과 | 케이스 수 | 10회 반복 시 호출 수 |
|---|---:|---:|
| `CORRECT` | 27 | 270 |
| `PARTIALLY_CORRECT` | 27 | 270 |
| `INCORRECT` | 36 | 360 |
| 합계 | 90 | 900 |

각 난이도에는 30개, 각 난이도·문제 유형 조합에는 다음 10개가 포함된다.

- `CORRECT`: 3개
- `PARTIALLY_CORRECT`: 3개
- `INCORRECT`: 4개

## 필드

- `questionId`: Supabase `public.questions.id`
- `difficulty`, `questionType`, `topic`: 층화 기준
- `questionText`, `questionTextKo`: 실제 문제 문구
- `gradingContext`: 그림 및 채점 참고 상황
- `modelAnswer`, `acceptedAnswers`: Supabase 등록 답안
- `caseId`: 평가 케이스의 안정적인 식별자
- `answerKind`: 답변 출처 및 생성 유형
- `answerText`: OpenAI 평가기에 전달할 학습자 답변
- `expectedResult`: 검토 대상 정답 라벨
- `criticalFalsePositive`: `CORRECT`로 오판하면 안 되는 중요 오답 여부
- `rationaleKo`: 사람이 라벨을 검토할 때 사용할 근거

## 사람 검토 필요

현재 데이터셋의 `reviewStatus`는 `HUMAN_REVIEW_REQUIRED`다. 특히
`PARTIALLY_CORRECT`와 `INCORRECT`의 경계는 다음 절차로 확정해야 한다.

1. 두 검토자가 AI 실행 결과를 보지 않고 독립적으로 라벨을 선택한다.
2. 질문의 명시적 요구, 난이도별 답변 형태, `gradingContext`를 기준으로
   판단한다.
3. 불일치 케이스는 합의하거나 제3자가 판정한다.
4. 확정 후 `reviewStatus`를 `HUMAN_APPROVED`로 변경한다.
5. 프롬프트 조정에 사용한 케이스는 최종 결과보고서용 held-out 세트에서
   제외한다.

사람 검토가 끝나기 전의 `expectedResult`는 평가 실행 구조를 준비하기 위한
후보 라벨이며, 자동 채점 정확도의 최종 gold label로 간주하지 않는다.

## 반복 실행 도구

사람 검토가 끝난 데이터셋은 다음 명령으로 평가한다.

```powershell
.\gradlew.bat answerAssessmentEval
```

현재처럼 `HUMAN_REVIEW_REQUIRED`인 초안 라벨로 실행 구조만 검증하려면 이를
명시해야 한다.

```powershell
.\gradlew.bat answerAssessmentEval -PevalAllowUnreviewed=true
```

기본 실행은 90개 케이스를 10회씩 총 900회 순차 호출한다. 각 라운드는 고정
seed를 사용해 순서만 섞으며, 결과는
`build/reports/answer-assessment-eval/<UTC 실행시각>/` 아래에 저장한다.

- `results.jsonl`: 호출별 예상·실제 라벨, 점수, 소요시간, 오류 유형
- `summary.json`: 혼동행렬, 케이스·그룹별 지표와 통과 기준
- `report.md`: 결과보고서에 첨부할 수 있는 요약 보고서

답변 피드백 원문과 OpenAI 원시 응답은 저장하지 않는다. 비공개
`config/application.yaml`의 `OPENAI_API_KEY`는 프로세스 환경에 키가 없을
때만 실행 시점에 읽으며 로그에는 출력하지 않는다.

### 기본 통과 기준

- 케이스별 반복 성공: `EASY` 10회 중 9회 이상, `NORMAL`·`HARD` 8회 이상
- 난이도·문제 유형별 정확도: `EASY` 95%, `NORMAL` 90%, `HARD` 85% 이상
- 전체 정확도: 90% 이상
- 유효 구조화 응답률: 99% 이상
- 3개 라벨 macro-F1: 0.85 이상
- `criticalFalsePositive=true`인 오답을 `CORRECT`로 판정: 0회

모든 기준을 만족해야 최종 `PASS`다. 실행 횟수, seed, 제한시간, 경로는 각각
`evalRounds`, `evalSeed`, `evalTimeoutSeconds`, `evalDataset`,
`evalOutputDir`, `evalModel` Gradle 속성으로 변경할 수 있다. 결과보고서에는
실제 사용한 모델, seed, 제한시간도 함께 기록된다.
