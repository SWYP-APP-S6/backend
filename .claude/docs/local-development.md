# 로컬 개발 환경

> 셋업/실행 레퍼런스. CLAUDE.md에서 포인터로만 참조(자동 로드 아님) — 필요할 때 읽는다.

- **인프라 = Docker Compose**: `compose.yaml`의 `postgres`(17) + `redis`(7). **Docker 실행 필요.**
- `./gradlew bootRun` — `spring-boot-docker-compose`(developmentOnly)가 compose의 postgres+redis를
  **자동 기동·연결**(ServiceConnection). 수동 DB 설치 불필요, 연결 설정을 손으로 적지 않는다.
- **전체 스택 도커**: `docker compose --profile app up` — postgres+redis+앱(멀티스테이지 `Dockerfile`,
  temurin 25). `app`은 profile이 걸려 기본 기동/자동관리에서 제외된다(순환 방지); 앱 컨테이너는
  서비스명(`postgres`/`redis`)으로 env 연결한다.
- `./gradlew bootTestRun` — 앱을 임시 PostgreSQL 컨테이너로 로컬 실행(`TestBackendApplication`).
  실제 PostgreSQL 미설치 상태로 굴려볼 때.
- Redis 클라이언트는 `spring-boot-starter-data-redis`, 속성 접두는 `spring.data.redis.*`.
