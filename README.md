# 말해봄 백엔드

어린이를 위한 AI 영어 말하기 학습 서비스의 Spring Boot 백엔드입니다.

## Google OAuth 로그인

로그인 흐름은 다음과 같습니다.

```text
GET /api/v1/auth/oauth/google/authorize
→ Google 로그인
→ /login/oauth2/code/google
→ 프론트엔드 /oauth/callback?code={oneTimeCode}
→ POST /api/v1/auth/oauth/exchange
→ Access Token 응답 + Refresh Token HttpOnly 쿠키
```

Access Token과 Refresh Token은 URL에 포함하지 않습니다. OAuth Callback은
60초 동안 한 번만 사용할 수 있는 임시 코드를 전달합니다.

### 필요한 환경 변수

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
FRONTEND_BASE_URL
COOKIE_SECURE
COOKIE_SAME_SITE
REFRESH_COOKIE_NAME
```

로컬 개발 예시:

```text
FRONTEND_BASE_URL=http://localhost:3000
COOKIE_SECURE=false
COOKIE_SAME_SITE=Lax
REFRESH_COOKIE_NAME=MALHAEBOM_REFRESH
```

운영 환경 예시:

```text
COOKIE_SECURE=true
REFRESH_COOKIE_NAME=__Secure-MALHAEBOM_REFRESH
```

Google Cloud Console의 승인된 리디렉션 URI에는 다음 주소를 등록합니다.

```text
http://localhost:8080/login/oauth2/code/google
```

운영 환경에서는 실제 API 도메인의 HTTPS 주소를 별도로 등록해야 합니다.

## 테스트

JDK 21을 사용합니다.

```powershell
.\gradlew.bat clean test
```

테스트에서는 H2를 사용하고 Flyway를 비활성화합니다. 실제 PostgreSQL에서는
`V1__create_auth_tables.sql`이 계정, OAuth 식별자, Refresh Token과 OAuth
로그인 코드 테이블을 생성합니다.
