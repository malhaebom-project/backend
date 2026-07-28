# 말해봄입니다.

## 로컬 개발 환경 설정

JWT 인증 설정을 사용하려면 `MALHAEBOM_AUTH_JWT_SECRET_BASE64` 값이 필요합니다.

`.env.example`을 복사해 프로젝트 루트에 `.env`를 만듭니다.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS 및 Linux:

```bash
cp .env.example .env
chmod 600 .env
```

Windows에서는 Git Bash, macOS와 Linux에서는 터미널에서 다음 명령으로 32바이트
난수 키를 Base64로 생성합니다.

```bash
openssl rand -base64 32
```

출력된 값을 `.env`에 입력합니다.

```dotenv
MALHAEBOM_AUTH_JWT_SECRET_BASE64=생성한_Base64_키
```

애플리케이션은 프로젝트 루트의 `.env`를 선택적으로 읽습니다.

Windows:

```powershell
.\gradlew.bat bootRun
```

macOS 및 Linux:

```bash
./gradlew bootRun
```

### 비밀키 관리

- 실제 `.env`는 Git에서 제외되며 커밋하지 않습니다.
- `.env.example`에는 실제 비밀키를 입력하지 않습니다.
- 팀원은 각자 생성한 로컬 개발용 키를 사용합니다.
- 운영 비밀키를 로컬 `.env`, README, 메신저 등에 저장하거나 공유하지 않습니다.
- AWS 배포에서는 SSM Parameter Store 또는 Secrets Manager에서
  `MALHAEBOM_AUTH_JWT_SECRET_BASE64` 환경변수로 주입합니다.
- 개발용 키를 교체하면 기존 키로 발급한 로컬 Access Token은 사용할 수 없습니다.

테스트는 `application-test.yaml`의 테스트 전용 키를 사용하므로 `.env`를 생성하지
않아도 실행할 수 있습니다.

```powershell
.\gradlew.bat test
```

macOS와 Linux에서는 다음 명령을 사용합니다.

```bash
./gradlew test
```
