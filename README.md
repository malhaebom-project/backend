# 말해봄 백엔드

Java 21과 Spring Boot 기반의 말해봄 백엔드 애플리케이션입니다.

## 애플리케이션 설정

기본 설정 파일인 `src/main/resources/application.yaml`은 다음 외부 파일을
선택적으로 불러옵니다.

- `config/application.yaml`: 공용 및 배포 설정
- 프로젝트 루트의 `.env`: 환경변수 형식의 선택 설정

현재 프로젝트는 `config/application.yaml`에 필요한 값을 직접 설정하므로
`.env`는 필수가 아닙니다. `config/`는 별도 Git 서브모듈이므로 처음 저장소를
받은 경우 다음 명령으로 초기화합니다.

```bash
git submodule update --init --recursive
```

### JWT

Access Token과 Refresh Token에는 서로 다른 비밀키를 사용합니다. 각 키는 최소
32바이트 이상의 충분히 긴 난수 문자열이어야 합니다.

```bash
openssl rand -base64 32
openssl rand -base64 32
```

각 명령의 출력값을 따로 설정합니다.

```yaml
jwt:
  access:
    secret-key: ACCESS_TOKEN_비밀키
    expiration: 1h
  refresh:
    secret-key: REFRESH_TOKEN_비밀키
    expiration: 14d
```

비밀키를 변경하면 기존 키로 발급한 토큰은 더 이상 사용할 수 없습니다.

### Refresh Token 쿠키

프런트엔드와 백엔드를 같은 사이트에서 제공하는 HTTPS 운영 환경의 권장 설정은
다음과 같습니다.

```yaml
cookie:
  refresh-token:
    path: /
    same-site: Lax
    secure: true
    http-only: true
    ttl: 14d
```

`domain`을 생략하면 Refresh Token은 API 호스트에만 전송되는 Host-only 쿠키로
생성됩니다. HTTPS 없이 `localhost` 또는 EC2 IP로 테스트할 때는 브라우저가
쿠키를 전송할 수 있도록 `secure: false`로 변경해야 합니다.

### 파일 업로드 제한

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB
```

### Google Cloud Text-to-Speech와 S3

TTS를 사용하지 않을 때는 다음 값을 유지합니다. 이 경우 Google Cloud와 S3
자격증명은 사용되지 않습니다.

```yaml
malhaebom:
  tts:
    enabled: false
```

TTS를 사용하려면 Google Cloud Text-to-Speech API를 활성화한 후 다음 값을
설정합니다. STT와 TTS는 아래의 공통 Google Cloud 자격증명을 사용합니다.

```yaml
malhaebom:
  tts:
    enabled: true

google:
  cloud:
    project-id: malhaebom-504606
    credentials:
      location: file:./config/google-credentials.json
  stt:
    enabled: true
    language-code: en-US
    timeout: 15s
    location: global
    recognizer-id: _
    model: short
  tts:
    language-code: en-US
    voice-name: en-US-Standard-C
    speaking-rate: 1.0
    pitch: 0.0
```

`google.cloud.credentials.location`을 설정하면 해당 JSON 키 파일을 사용합니다.
설정을 생략하면 Google Cloud Java 라이브러리의 Application Default Credentials
(ADC)를 사용하므로 `GOOGLE_APPLICATION_CREDENTIALS`, 로컬 ADC 또는 실행 환경에
연결된 서비스 계정을 자동으로 탐색합니다.

현재 S3 클라이언트 구현은 정적 자격증명을 사용하므로 TTS를 활성화할 때
`access-key`와 `secret-key`가 모두 필요합니다.

```yaml
aws:
  s3:
    access-key: IAM_ACCESS_KEY_ID
    secret-key: IAM_SECRET_ACCESS_KEY
    region: ap-northeast-2
    bucket: malhaebom-tts
    base-url: https://malhaebom-tts.s3.ap-northeast-2.amazonaws.com
    key-prefix: tts/questions
```

IAM 자격증명에는 최소한 다음 리소스에 대한 `s3:PutObject` 권한이 필요합니다.

```text
arn:aws:s3:::malhaebom-tts/tts/questions/*
```

생성한 음원은 `tts/questions/{questionId}.mp3` 경로에 저장됩니다. `base-url`은
해당 경로를 포함하지 않아야 합니다. S3 URL을 브라우저에 직접 반환하므로 객체를
읽을 수 있도록 공개 읽기를 구성하거나, 비공개 S3 앞에 CloudFront를 연결하고
CloudFront 배포 도메인을 `base-url`로 사용해야 합니다.

AWS Access Key를 파일에 저장하지 않고 EC2 IAM Role을 사용하려면 S3 클라이언트가
AWS 기본 자격증명 공급자 체인을 사용하도록 코드를 먼저 변경해야 합니다.

## 데이터베이스

현재 별도의 `spring.datasource` 설정이 없으면 내장 H2 데이터베이스가 사용됩니다.
메모리 DB의 데이터는 애플리케이션 또는 컨테이너를 재시작하면 유지되지 않습니다.
운영 데이터를 보존하려면 PostgreSQL 또는 RDS 연결 설정과 스키마 관리 방법을
별도로 구성해야 합니다.

## 실행

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

macOS 및 Linux:

```bash
./gradlew bootRun
```

기본 운영 구현은 Google Cloud Speech-to-Text V2를 사용합니다. 접근 권한이 있는
`config` 저장소를 내려받으면 STT와 TTS가 함께 사용하는 서비스 계정 자격증명도
준비됩니다. 로컬 프로필은 `config/google-credentials.json`, 운영 프로필은
`/app/config/google-credentials.json`을 읽습니다. 경로 설정을 제거하면 Google Cloud
Java 라이브러리의 ADC를 사용합니다.

## 테스트

테스트는 테스트 전용 설정을 사용하므로 `.env` 없이 실행할 수 있습니다.

Windows PowerShell:

```powershell
.\gradlew.bat test
```

macOS 및 Linux:

```bash
./gradlew test
```

## 비밀정보 관리

- JWT 키, Google private key, AWS Access Key를 README, 이슈 또는 메신저에
  공유하지 않습니다.
- 비밀정보가 노출되면 해당 키를 즉시 폐기하고 새 키로 교체합니다.
- `config/application.yaml`의 실제 운영값은 접근 권한을 제한해 관리합니다.
- `.env`를 사용하도록 전환하는 경우에도 실제 `.env`는 Git에 커밋하지 않습니다.
