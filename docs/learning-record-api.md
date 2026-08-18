# 학습 기록 API 집계 기준

이 문서는 어린이 학습 기록 API가 원본 학습 데이터를 조회하고 집계하는 기준을
정의한다. 응답 스키마 자체보다 각 응답 값의 포함 범위와 계산식을 명확히 하여
백엔드 구현, 프론트엔드 표시, 테스트가 같은 의미를 사용하도록 하는 것이
목적이다.

## 적용 범위

| 기능 | 엔드포인트 |
| --- | --- |
| 학습 기록 조회 | `GET /api/v1/children/{childId}/learning-history` |
| 학습 통계 조회 | `GET /api/v1/children/{childId}/statistics` |
| 최근 오답 조회 | `GET /api/v1/children/{childId}/wrong-answers` |

어린이 프로필 응답의 `totalStudyCount`, `totalCorrectRate`도 이 문서의 전체
학습 통계 기준을 동일하게 사용한다.

## 공통 원칙

- 요청한 어린이가 로그인 사용자의 활성 어린이 프로필인지 먼저 확인한다.
- 학습 기록과 학습 통계는 조회 시점에 원본 학습 세션과 문제 기록을 집계한다.
  별도의 통계 테이블이나 비동기 집계 결과를 사용하지 않는다.
- 학습 기록과 학습 통계에는 상태가 `COMPLETED`인 세션만 포함한다.
  `IN_PROGRESS`, `CANCELED` 세션은 제외한다.
- 문제의 `active` 여부는 과거 기록의 포함 여부에 영향을 주지 않는다. 완료 후
  문제가 비활성화되어도 기존 학습 기록과 통계에는 계속 포함한다.
- 학습 시각은 데이터베이스에 UTC 기준으로 저장한다. 날짜 요청과 연속 학습일은
  `Asia/Seoul` 날짜를 기준으로 해석하고, API 시각 응답에는 UTC 오프셋 `Z`를
  포함한다.
- 정답률은 퍼센트 값이며 소수점 첫째 자리까지 반올림한다.
- 집계할 기록이 없으면 횟수와 시간은 `0`, 정답률은 `0.0`으로 반환한다.

## 학습 기록 조회

### 요청 조건

| 파라미터 | 기본값 | 제약 및 의미 |
| --- | --- | --- |
| `page` | `0` | 0부터 시작하며 음수일 수 없다. |
| `size` | `10` | 1 이상 50 이하여야 한다. |
| `startDate` | 없음 | `Asia/Seoul` 기준 지정한 날짜의 `00:00`부터 조회한다. 생략하면 `1970-01-01 00:00`부터 조회한다. |
| `endDate` | 없음 | `Asia/Seoul` 기준 지정한 날짜 전체를 포함한다. 생략하면 `9999-01-01 00:00` 전까지 조회한다. |

`startDate`와 `endDate`를 모두 지정한 경우 `startDate`는 `endDate`보다 늦을 수
없다. 날짜를 생략하면 위 기본 경계를 사용하므로 일반적인 운영 데이터에
대해서는 사실상 전체 기간을 조회한다.

기간 조건은 완료 시각 `completedAt`을 기준으로 다음과 같이 적용한다.

```text
startDate 00:00 KST <= completedAt < endDate 다음 날 00:00 KST
```

따라서 `endDate` 당일의 모든 완료 기록이 포함되고, 다음 날 `00:00`에 완료된
기록은 포함되지 않는다. 이 경계는 UTC 저장 시각으로 변환한 뒤 조회한다.

### 정렬과 페이징

1. `completedAt` 내림차순
2. 완료 시각이 같으면 `sessionId` 내림차순

두 번째 정렬 조건은 같은 완료 시각의 기록이 여러 개여도 페이지 간 순서가
안정적으로 유지되도록 한다.

### 항목별 계산 기준

| 응답 필드 | 기준 |
| --- | --- |
| `sessionId` | 완료 학습 세션 ID |
| `topicName` | 세션에 저장된 학습 주제의 표시 이름 |
| `difficulty` | 세션에 저장된 난이도 |
| `questionCount` | 세션에 포함된 전체 문제 수 |
| `correctCount` | `correct = true`인 세션 문제 수 |
| `correctRate` | `correctCount / questionCount * 100` |
| `studySeconds` | `completedAt - startedAt`의 초 단위 값 |
| `completedAt` | UTC 오프셋 `Z`가 포함된 세션 완료 시각 |

`startedAt` 또는 `completedAt`이 없거나 계산 결과가 음수이면 `studySeconds`는
`0`으로 처리한다.

## 학습 통계 조회

학습 통계는 기간 제한 없이 해당 어린이의 전체 완료 세션을 집계한다.

### 전체 통계

| 응답 필드 | 계산 기준 |
| --- | --- |
| `totalSessionCount` | 완료 세션 수 |
| `totalStudySeconds` | 완료 세션별 `studySeconds`의 합 |
| `averageCorrectRate` | 전체 정답 문제 수 / 전체 문제 수 * 100 |
| `consecutiveStudyDays` | 아래 연속 학습일 기준에 따른 일수 |

`averageCorrectRate`는 세션별 정답률의 산술 평균이 아니다. 문제 수가 다른
세션도 전체 문제 수를 기준으로 가중되어 집계된다.

예를 들어 3문제 중 2문제를 맞힌 세션과 1문제 중 1문제를 맞힌 세션이 있으면
다음과 같이 계산한다.

```text
(2 + 1) / (3 + 1) * 100 = 75.0
```

### 주제별 통계

| 응답 필드 | 계산 기준 |
| --- | --- |
| `topicName` | 주제 표시 이름 |
| `questionCount` | 해당 주제의 완료 세션에 포함된 전체 문제 수 |
| `correctRate` | 해당 주제의 정답 문제 수 / 문제 수 * 100 |

문제 기록 자체의 주제가 아니라 학습 세션에 저장된 주제를 기준으로 그룹화한다.
응답은 학습 주제 ID 오름차순으로 정렬한다. 기록이 없는 주제는 응답 목록에
포함하지 않는다.

### 연속 학습일

- 완료 시각의 날짜를 학습일로 사용한다.
- 같은 날짜에 완료한 세션이 여러 개여도 한 학습일로 계산한다.
- 현재 날짜는 `Asia/Seoul` 시간대를 기준으로 판단한다.
- 오늘 학습 기록이 있으면 오늘부터 과거 방향으로 연속된 날짜를 계산한다.
- 오늘 학습 기록이 없으면 어제부터 계산한다. 따라서 오늘 아직 학습하지
  않았더라도 어제까지 이어진 연속 기록은 유지된다.
- 오늘과 어제 모두 학습 기록이 없으면 `0`을 반환한다. 계산을 시작한 날짜부터
  과거로 이동하다 빈 날짜를 만나면 그 직전까지의 일수를 반환한다.

예를 들어 오늘이 8월 18일일 때 8월 16일, 17일, 18일에 완료 기록이 있으면
`3`이고, 8월 16일과 17일에만 있으면 오늘 중에는 여전히 `2`이다.

## 최근 오답 조회

최근 오답은 통계가 아니라 저장된 답변 시도 목록을 조회한다. 학습 기록 및
통계와 달리 세션 상태를 `COMPLETED`로 제한하지 않는다.

### 포함 기준

- 요청한 어린이의 답변만 포함한다.
- `PARTIALLY_CORRECT`, `INCORRECT`, `UNRECOGNIZED` 결과를 포함한다.
- `CORRECT` 결과는 제외한다.
- 문제 단위로 중복을 제거하지 않는다. 같은 문제의 여러 오답 시도도 각각
  별도의 항목으로 포함한다.
- 문제의 현재 `active` 여부와 관계없이 과거 답변을 포함한다.

### 정렬과 개수

1. `answeredAt`(`submittedAt`) 내림차순
2. 제출 시각이 같으면 `answerId` 내림차순
3. 최대 10건

### 필드의 데이터 출처

| 응답 필드 | 출처 |
| --- | --- |
| `answerId` | 답변 ID |
| `questionId` | 답변이 연결된 문제 ID |
| `questionText` | 연결된 문제의 현재 영문 문구 |
| `imageUrl` | 연결된 문제 이미지 경로를 외부 URL로 변환한 값 |
| `answerText` | 제출된 답변 문구 |
| `modelAnswer` | 답변 생성 시 저장한 모범 답안 스냅샷 |
| `feedbackText` | 해당 답변에 생성된 피드백 |
| `answeredAt` | UTC 오프셋 `Z`가 포함된 답변 제출 시각 |

`questionText`와 `imageUrl`은 현재 문제 데이터를 참조하고, `modelAnswer`는 답변
당시의 스냅샷을 사용한다.

## 문제 변경 운영 방침과 과거 기록

운영 중 문제 문구나 허용 답안을 변경해야 하는 경우 기존 문제를 직접 수정하지
않는다.

1. 기존 문제를 비활성화한다.
2. 변경된 내용으로 새 문제를 등록한다.

기존 학습 세션과 답변은 비활성화된 기존 문제를 계속 참조하고, 새 학습부터 새
문제를 사용한다. 이 방침을 통해 현재 문제 데이터를 참조하는 최근 오답에서도
과거 문제 문구가 임의로 달라지는 것을 방지한다.

## 구현 및 검증 위치

- API 진입점:
  [`LearningRecordController`](../src/main/java/com/malhaebom/malhaebom/presentation/LearningRecordController.java)
- 집계 및 계산:
  [`LearningRecordQueryService`](../src/main/java/com/malhaebom/malhaebom/service/LearningRecordQueryService.java)
- 집계 쿼리:
  [`LearningSessionRepository`](../src/main/java/com/malhaebom/malhaebom/domain/learning/repository/LearningSessionRepository.java),
  [`AnswerRepository`](../src/main/java/com/malhaebom/malhaebom/domain/learning/repository/AnswerRepository.java)
- API 통합 검증:
  [`LearningRecordControllerJpaTest`](../src/test/java/com/malhaebom/malhaebom/integration/learning/LearningRecordControllerJpaTest.java)
- 집계 쿼리 통합 검증:
  [`LearningHistoryRepositoryJpaTest`](../src/test/java/com/malhaebom/malhaebom/integration/learning/LearningHistoryRepositoryJpaTest.java)

집계 규칙을 변경할 때는 이 문서와 해당 통합 테스트를 함께 변경한다.
