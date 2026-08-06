# Local Fake STT와 Postman으로 학습 흐름 확인

`local-fake-stt` 프로필은 로컬에서 다음 API 흐름을 직접 확인하기 위한 전용
프로필이다.

1. 학습 세션 생성
2. 문제 조회
3. Multipart 음성 업로드
4. 답변 제출
5. 완료된 학습 세션 확인

이 프로필은 실제 AWS를 호출하지 않는다. 업로드된 파일을 디코딩하지 않고 항상
다음 STT 결과를 반환한다.

```json
{
  "transcript": "He is running.",
  "confidence": 0.94,
  "provider": "LOCAL_FAKE_STT"
}
```

프로필이 활성화되면 위 답변을 정답으로 인정하는 샘플 문제도 H2 메모리 DB에
자동 등록된다. 서버를 종료하면 생성한 세션과 답변은 사라진다.

## 서버 실행

Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local-fake-stt"
```

macOS 및 Linux:

```bash
./gradlew bootRun --args="--spring.profiles.active=local-fake-stt"
```

IntelliJ에서는 Run/Debug Configurations에서
`Malhaebom Local Fake STT`를 선택해 실행한다.

서버 기본 주소는 다음과 같다.

```text
http://localhost:8080
```

## Postman 컬렉션 설정

1. Postman에서 `Import`를 선택한다.
2. `postman/Malhaebom Local Fake STT.postman_collection.json`을 가져온다.
3. 컬렉션의 `Variables`에서 `baseUrl`이
   `http://localhost:8080/api/v1`인지 확인한다.
4. `3. 음성 업로드` 요청의 `Body > form-data > audio`에
   `postman/files/fake-audio.mp3`가 선택되어 있는지 확인한다. 컬렉션에는 현재
   워크스페이스의 절대 경로와 `audio/mpeg` MIME이 미리 설정되어 있다.
5. 컬렉션 요청을 1번부터 5번까지 순서대로 보낸다.

프로젝트를 다른 경로로 이동했거나 Postman이 로컬 파일 접근을 차단하면
`Body > form-data > audio`에서 다음 파일을 다시 선택한다.

```text
postman/files/fake-audio.mp3
```

각 요청의 테스트 스크립트가 다음 요청에 필요한 값을 컬렉션 변수에 자동으로
저장한다.

| 요청 | 저장되는 변수 |
| --- | --- |
| `1. 학습 세션 생성` | `sessionId` |
| `2. 현재 문제 조회` | `sessionQuestionId` |
| `3. 음성 업로드` | `speechAnswerId`, `transcript` |

`Idempotency-Key`는 Postman의 `{{$guid}}` 동적 변수를 사용하므로 음성 업로드마다
새 UUID가 전송된다.

## 예상 결과

`2. 현재 문제 조회`:

```json
{
  "questionText": "What is the boy doing?"
}
```

`3. 음성 업로드`:

```json
{
  "speechAnswerId": 1,
  "transcript": "He is running.",
  "confidence": 0.94,
  "audioUrl": null
}
```

`4. 답변 제출`:

```json
{
  "answerText": "He is running.",
  "result": "CORRECT",
  "score": 100,
  "attemptNo": 1,
  "matchedKeywords": [],
  "missingKeywords": [],
  "feedbackText": "정확하고 또박또박 잘 말했어요!",
  "feedbackTtsUrl": null,
  "canRetry": false
}
```

`5. 학습 세션 확인`:

```json
{
  "status": "COMPLETED",
  "correctCount": 1
}
```

## H2 콘솔

필요하면 다음 주소에서 H2 데이터를 직접 확인할 수 있다.

```text
http://localhost:8080/h2-console
```

연결 값:

| 항목 | 값 |
| --- | --- |
| JDBC URL | `jdbc:h2:mem:malhaebom-local` |
| User Name | `sa` |
| Password | 빈 값 |

## 주의사항

- `local-fake-stt` 프로필은 로컬 확인용이며 개발 공유 서버나 운영 환경에서
  활성화하지 않는다.
- `postman/files/fake-audio.mp3`는 실제 MP3가 아니라 비어 있지 않은 로컬 테스트
  페이로드다. Fake STT는 파일 내용을 디코딩하지 않으므로 이 프로필에서만
  사용한다.
- 답변 제출 API는 음성 업로드 응답의 `speechAnswerId`가 현재 문제에서 완료된
  음성 답변인지 확인하고, 함께 전달된 `answerText`가 해당 음성 인식 결과와
  일치하는지 검증한다.
- 키워드 분석과 피드백 TTS는 아직 구현되지 않아 키워드 목록은 빈 배열,
  `feedbackTtsUrl`은 `null`을 반환한다.
