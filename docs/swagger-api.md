# Swagger API 문서 접근 및 운영 정책

## 접근 방법

애플리케이션이 실행 중일 때 다음 경로에서 Swagger UI에 접근한다.

```text
{서버 기본 URL}/swagger-ui/index.html
```

로컬 기본 주소는 <http://localhost:8080/swagger-ui/index.html>이다. 운영 환경에서는
`{서버 기본 URL}`을 실제 배포 서버 주소로 바꾼다.

OpenAPI 명세 원문은 그룹별로 다음 경로에서 확인할 수 있다.

| 그룹 | 대상 | OpenAPI JSON |
| --- | --- | --- |
| `user-api` | `/api/v1/**` 중 관리자 API를 제외한 일반 사용자 API | `/v3/api-docs/user-api` |
| `admin-api` | `/api/v1/admin/**` 관리자 API | `/v3/api-docs/admin-api` |

Swagger UI의 `Select a definition`에서 그룹을 전환한다. 기본 선택 그룹은
`user-api`이다.

## 인증

JWT 인증이 필요한 API는 Swagger UI의 `Authorize`에서 로그인 API로 발급받은
Access Token 값만 입력한다. `Bearer` 접두사는 Swagger UI가 요청에 추가한다.

로그인과 토큰 재발급 응답의 Refresh Token은 `HttpOnly` 쿠키로 전달된다. 쿠키의
이름, `Path`, `SameSite`, `Secure`, 만료 시간은 실행 환경의 설정을 따르며 자세한
계약은 각 인증 API 응답 문서에서 확인한다. Swagger 문서에 API가 노출되는 것과
실제 API 접근 권한은 별개이며, 요청 시 애플리케이션의 인증·인가 검사를 그대로
거친다.

## 운영 정책

현재 Springdoc 설정은 공통 `application.yaml`에 있으므로 로컬과 운영 프로필에서
모두 Swagger UI와 OpenAPI JSON을 제공한다. 운영에서 문서를 외부에 공개하지 않을
경우 배포 환경 또는 별도 프로필에서 다음 설정을 적용하고, 필요하면 리버스 프록시나
네트워크 접근 제어도 함께 사용한다.

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

운영 Swagger를 사용할 때는 다음 원칙을 지킨다.

- 실제 비밀번호, JWT, 쿠키, 외부 서비스 키 등 비밀정보를 문서 예시에 기록하거나
  공유하지 않는다.
- 운영 환경의 `Try it out`은 실제 데이터에 요청을 보낸다. 생성·수정·삭제 API는
  의도한 운영 작업일 때만 실행하고 문서 확인용 테스트 데이터를 만들지 않는다.
- API 요청·응답, 상태 코드, 오류 코드, 쿠키 또는 비동기 계약이 바뀌면 관련 구현과
  Swagger 문서를 같은 변경에서 갱신한다.
- 새 API는 기본적으로 `user-api`에 포함한다. `/api/v1/admin/**`에 등록된 API만
  `admin-api`로 분리한다.
- 예시는 실제 프런트엔드 계약과 일치하는 대표값을 사용하되, 운영 사용자 데이터나
  식별자를 그대로 복사하지 않는다.
- Controller 설명, 성공 응답과 도메인 오류 응답은 공통 OpenAPI 커스터마이저가 각
  그룹에 연결된 상태를 유지한다.

Swagger 문서는 클라이언트 연동을 위한 계약 자료다. 문서와 실제 응답이 다르면
배포된 API 동작을 우선 확인하고, 차이를 문서 누락으로 남기지 말고 함께 수정한다.
