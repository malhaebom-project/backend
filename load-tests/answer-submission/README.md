# 답안 제출 비동기 부하 테스트

실제 답안 제출 HTTP 경로에 같은 텍스트를 가진 서로 다른 세션을 병렬로
전송한다. OpenAI 응답 대기 중 Tomcat 요청 스레드가 반환되는지, 동시 제한 32건과
과부하 응답이 지켜지는지, 동시에 호출한 다른 API가 영향을 받는지 확인한다.

## 사전 요구 사항

- Java 21과 Gradle Wrapper
- k6
- Python 3
- AWS 실행 시 OpenSSH와 EC2 접속 키

결과와 fixture manifest는 `load-tests/results/`에 생성되며 Git에서 제외된다.

## 로컬 실행

아래 명령은 프로젝트 루트의 서로 다른 PowerShell에서 실행한다. DB 파일 이름은
실행마다 새 값으로 바꿔 이전 결과와 격리한다.

```powershell
python load-tests/answer-submission/fake-openai.py --delay-seconds 5
```

가짜 서버는 300건 burst가 운영체제의 작은 기본 TCP backlog에서 거절되지 않도록
1024개의 대기 연결을 허용한다.

백엔드와 fixture 도구가 같은 파일 H2를 사용하도록 환경을 설정한다.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:file:./.private/load-tests/answer-load-20260821;MODE=PostgreSQL;AUTO_SERVER=TRUE'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'update'
$env:SPRING_AI_OPENAI_API_KEY = 'load-test-local'
$env:SPRING_AI_OPENAI_BASE_URL = 'http://127.0.0.1:18080'
$env:SPRING_AI_OPENAI_TIMEOUT = '20s'
$env:SPRING_AI_OPENAI_MAX_RETRIES = '0'
$env:GCP_STT_ENABLED = 'false'
New-Item -ItemType Directory -Force load-tests/results/local | Out-Null
./gradlew.bat loadTestServer --no-daemon 2>&1 `
  | Tee-Object -FilePath load-tests/results/local/backend.log
```

백엔드가 준비된 후 fixture 610건을 만든다. 같은 datasource 환경변수를 설정한
PowerShell에서 실행해야 한다.

```powershell
./gradlew.bat loadTestFixtures --no-daemon `
  -PloadTestAction=seed `
  -PloadTestRunId=local-20260821 `
  -PloadTestManifest=load-tests/results/local/fixture-manifest.json
```

네 단계를 실행한다.

```powershell
./load-tests/answer-submission/run-stages.ps1 `
  -Manifest load-tests/results/local/fixture-manifest.json `
  -ResultRoot load-tests/results/local
```

각 단계는 부하 전 probe와 부하 중 probe를 분리해 기록한다. 단계 종료 후
OpenAI active와 Hikari pending이 10초 연속 0이 될 때까지 기다리며, 기본
300초 안에 회복하지 않으면 다음 단계로 넘어가지 않는다. probe 지연 판정은
`max(baseline p95 × 2, 1초)`를 사용한다. 같은 결과 디렉터리에 다시 실행하면
해당 단계에서 생성한 파일만 새 결과로 교체된다.

가짜 OpenAI의 최대 동시 요청은 다음 주소에서도 확인할 수 있다.

```powershell
Invoke-RestMethod http://127.0.0.1:18080/metrics
```

종료 전 fixture를 정리한다.

```powershell
./gradlew.bat loadTestFixtures --no-daemon `
  -PloadTestAction=cleanup `
  -PloadTestManifest=load-tests/results/local/fixture-manifest.json
```

## AWS 실행

Actuator 관리 포트는 EC2 loopback에만 공개된다. 먼저 외부에서 9090 포트가
닫혀 있는지 확인한 뒤 SSH tunnel을 연다.

```powershell
Test-NetConnection 3.35.11.125 -Port 9090
ssh -N -L 19090:127.0.0.1:9090 -i C:\path\to\key.pem ubuntu@3.35.11.125
```

`SPRING_PROFILES_ACTIVE=prod`와 운영 datasource 설정으로 fixture를 만든 뒤 같은
manifest를 k6에 전달한다. fixture 생성·정리는 반드시 동일한 DB를 사용해야 한다.
테스트 배포 환경에서도 `SPRING_AI_OPENAI_MAX_RETRIES=0`으로 지정해 호출 수와
응답 분류가 자동 재시도의 영향을 받지 않게 한다.

```powershell
./load-tests/answer-submission/run-stages.ps1 `
  -BaseUrl http://3.35.11.125 `
  -ManagementUrl http://127.0.0.1:19090 `
  -Manifest load-tests/results/aws/fixture-manifest.json `
  -ResultRoot load-tests/results/aws `
  -DockerContainer backend-was-1 `
  -SshHost ubuntu@3.35.11.125 `
  -SshIdentityFile C:\path\to\key.pem
```

실행 중 일반 사용자 요청을 피하고, 종료 후에는 fixture cleanup과
`malhaebom.answer.assessment.active=0`, `hikaricp.connections.pending=0`을
확인한다. 실제 OpenAI는 완료된 permit을 뒤 요청이 다시 사용할 수 있으므로 네
단계 합계 최대 610회의 과금 호출이 발생할 수 있다.
`DockerContainer`를 지정하면 단계별 Docker CPU·메모리와 해당 단계의 컨테이너
로그도 함께 보관된다.

## 결과 보고서 생성

```powershell
./load-tests/answer-submission/build-report.ps1 `
  -ResultRoot load-tests/results/local `
  -OutputPath .private/docs/backend-architecture/answer-submission-load-test.md `
  -Environment local-fake-openai `
  -GitCommit (git rev-parse --short HEAD) `
  -FixtureRunId local-20260821
```

자동 생성된 표에 원시 지표의 지속 시간과 결론을 보완한다. 준비 limiter는
Tomcat 포화, Hikari pending 지속 또는 probe API 지연이 실제로 확인될 때만 다시
검토한다.

`spring.jpa.open-in-view`가 켜져 있으면 비동기 dispatch 동안 JPA 연결이 요청에
묶이는지 함께 확인한다. Hikari 고갈이 재현되면 preparation limiter를 추가하기
전에 `SPRING_JPA_OPEN_IN_VIEW=false`로 원인을 교차 검증한다.
