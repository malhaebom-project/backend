# 말해봄 백엔드

영어 말하기 학습 서비스 **말해봄**의 Java 21·Spring Boot 백엔드입니다.
학습 세션과 답안 제출, Google Cloud Speech-to-Text 기반 음성 인식,
OpenAI 기반 답안 평가, 학습 기록 및 관리자용 문제 관리를 제공합니다.

## 기술 스택

- Java 21, Spring Boot 4.1, Gradle
- Spring Web MVC, Spring Data JPA, Bean Validation
- PostgreSQL(Supabase, 운영), H2(로컬·테스트)
- OpenAI, Google Cloud Speech-to-Text·Text-to-Speech, Amazon S3
- Springdoc OpenAPI, Actuator, Micrometer, Prometheus

## 프로젝트 문서

- [Swagger API 문서 접근 및 운영 정책](docs/swagger-api.md)
- [학습 기록 API 집계 기준](docs/learning-record-api.md)
- [답안 평가 rate limit·대기열 지표](docs/answer-assessment-metrics.md)
- [OpenAI 답안 평가 데이터셋과 반복 평가](docs/answer-assessment-evaluation-dataset.md)
- [답안 제출 비동기 부하 테스트](load-tests/answer-submission/README.md)

## 시작하기

### 요구 사항

- JDK 21
- Git
- Google STT나 OpenAI 답안 평가 등 외부 연동을 실행하려면 `config`
  저장소 접근 권한과 각 provider의 유효한 자격증명

Gradle은 별도 설치하지 않고 저장소의 Gradle Wrapper를 사용합니다.

### 저장소와 비공개 설정 준비

`config/`는 별도 비공개 Git 서브모듈입니다. 저장소를 처음 받은 뒤 다음 명령으로
초기화합니다.

```powershell
git submodule update --init --recursive
```

공통 설정인 `src/main/resources/application.yaml`은 다음 외부 파일을 선택적으로
불러옵니다.

- `config/application.yaml`: JWT, 쿠키, AWS 등 비공개 설정
- 프로젝트 루트의 `.env`: 환경변수 형식의 선택 설정

현재 개발 환경은 비공개 `config/application.yaml`을 사용하므로 `.env`가 필수는
아닙니다. 실제 비밀값은 README, 이슈, 로그 또는 추적되는 설정 파일에 기록하지
않습니다.

### 로컬 실행

기본 프로필은 `local`이며, 인메모리 H2 데이터베이스를 사용합니다.

```powershell
.\gradlew.bat bootRun
```

macOS와 Linux에서는 `./gradlew bootRun`을 사용합니다. 애플리케이션 기본 포트는
`8080`, Actuator 관리 포트는 `9090`입니다.

로컬 실행 후 Swagger UI에서 API를 확인할 수 있습니다.

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- 사용자 API 명세: <http://localhost:8080/v3/api-docs/user-api>
- 관리자 API 명세: <http://localhost:8080/v3/api-docs/admin-api>
- 상태 확인: <http://localhost:9090/actuator/health>

현재 배포 주소는 다음과 같습니다.

- 프런트엔드: <https://frontend-eight-psi-26.vercel.app/>
- 백엔드 Swagger UI: <http://3.35.11.125/swagger-ui/index.html>

## 실행 프로필과 데이터베이스

| 프로필 | 데이터베이스 | 주요 용도 |
| --- | --- | --- |
| `local` (기본) | 인메모리 H2, PostgreSQL 호환 모드 | 로컬 개발 |
| `prod` | Supabase PostgreSQL | 운영 배포 |
| 테스트 설정 | 인메모리 H2 | 자동 테스트 |

운영 프로필은 `DB_PASSWORD`가 필요하며 다음처럼 실행합니다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:DB_PASSWORD = "<Supabase database password>"
.\gradlew.bat bootRun
```

운영 배포는 `docker-compose.prod.yml`에서 `prod` 프로필을 활성화하고 `config/`를
컨테이너의 `/app/config`에 읽기 전용으로 연결합니다. 현재 JPA 스키마 정책은 로컬과
운영 모두 `ddl-auto: update`이므로 스키마 변경을 배포하기 전에 영향을 확인해야
합니다.

## 외부 연동 설정

### OpenAI 답안 평가

OpenAI API 키는 `OPENAI_API_KEY`로 주입합니다. 기본 모델과 답안 평가의 대기열,
RPM·TPM 제한은 `src/main/resources/application.yaml`의 `spring.ai.openai`와
`malhaebom.answer-assessment`에서 관리합니다.

답안 평가는 provider quota가 부족할 때 bounded FIFO 대기열을 사용합니다. 관련
설정과 관측 지표는 [답안 평가 지표 문서](docs/answer-assessment-metrics.md)를
참고합니다.

### Google Cloud STT·TTS

STT와 TTS는 공통 `gcp.credentials`를 사용합니다. 기본 설정은 STT 활성화,
TTS 비활성화입니다.

```yaml
gcp:
  credentials: file:./config/google-credentials.json
  stt:
    enabled: true
    language-code: en-US
    timeout: 15s
    location: us
    recognizer-id: _
    model: chirp_3
    adaptation-boost: 5
  tts:
    enabled: false
    language-code: en-US
    voice-name: en-US-Standard-C
```

`gcp.credentials`를 생략하면 Google Cloud Java 라이브러리의 Application Default
Credentials를 사용합니다. 음성 답변은 최대 5 MB이며, STT 요청에는 현재 문제의
허용 답안 목록이 inline PhraseSet으로 포함됩니다. `adaptation-boost`는 `0`보다
크고 `20` 이하여야 합니다.

### TTS와 Amazon S3

TTS를 활성화하면 생성한 MP3를 `aws.s3.key-prefix` 아래에 저장하고
`aws.s3.base-url`을 기준으로 공개 URL을 생성합니다. 현재 S3 클라이언트는 정적
자격증명을 사용하므로 `aws.s3.access-key`와 `aws.s3.secret-key`가 모두
필요합니다. IAM에는 대상 prefix에 대한 `s3:PutObject` 권한이 있어야 합니다.

### JWT와 Refresh Token 쿠키

Access Token과 Refresh Token에는 서로 다른 32바이트 이상의 비밀키를 사용합니다.
Refresh Token은 HttpOnly 쿠키로 전달됩니다. HTTPS 운영 환경에서는 `secure: true`를
사용하고, HTTPS가 없는 localhost나 IP 기반 개발 환경에서는 `secure: false`로
설정합니다. 키를 변경하면 기존 토큰은 더 이상 유효하지 않습니다.

## 테스트와 평가

일반 테스트는 외부 서비스를 호출하는 `live` 태그를 제외하며 `.env` 없이 실행할
수 있습니다.

```powershell
.\gradlew.bat test --no-daemon
```

외부 서비스를 실제 호출하는 테스트는 명시적으로 분리되어 있습니다.

```powershell
.\gradlew.bat liveTest --no-daemon
```

OpenAI 답안 평가 품질을 반복 측정하는 유료 평가도 일반 테스트와 분리되어 있습니다.
기본 데이터셋, 실행 횟수, 결과 경로와 사람 검토 절차는
[평가 데이터셋 문서](docs/answer-assessment-evaluation-dataset.md)를 먼저 확인합니다.

```powershell
.\gradlew.bat answerAssessmentEval --no-daemon
```

IntelliJ IDEA에서는 Gradle 동기화 후 저장소가 공유하는 `Test`, `Live Test`,
`Answer Assessment Eval`, `Load Test Server` Run Configuration을 사용할 수 있습니다.
일반 `test` 작업은 live OpenAI 평가나 부하 테스트를 실행하지 않습니다.

## 운영과 관측

Actuator는 별도 관리 포트에서 `health`, `metrics`, `prometheus` endpoint를
제공합니다. 운영과 부하 테스트에서는 답안 평가 대기열, provider RPM·TPM,
OpenAI 사용량·실패 원인, HTTP latency와 Hikari 상태를 함께 확인합니다.

답안 제출 부하 테스트는 fixture와 별도 실행 환경이 필요하고 실제 provider 비용이
발생할 수 있어 CI에서 자동 실행하지 않습니다. 실행 절차는
[부하 테스트 문서](load-tests/answer-submission/README.md)를 따릅니다.

## 비밀정보 관리

- JWT 키, Google 서비스 계정 키, AWS Access Key, OpenAI API Key, DB 비밀번호를
  Git에 커밋하거나 문서와 로그에 남기지 않습니다.
- 비밀정보가 노출되면 해당 키를 즉시 폐기하고 새 키로 교체합니다.
- 비공개 `config/application.yaml`과 자격증명 파일은 접근 권한을 제한합니다.
- `.env`를 사용하는 경우에도 실제 `.env`는 Git에 커밋하지 않습니다.
