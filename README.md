# SWYP Backend

SWYP 앱의 백엔드 REST API 서버. (관리자 백오피스 + 소비자·판매자용 API — 앱 유저 기능은 개발 중.)

## 스택

- **Java 25** (LTS) · **Spring Boot 4.1** · **Gradle** (Kotlin DSL)
- **PostgreSQL 17** (스키마는 Flyway 마이그레이션) · **Redis 7** (refresh 토큰)
- 인증: JWT access + rotating refresh · API 문서: springdoc(OpenAPI)
- 테스트: JUnit 5 + Testcontainers(실제 PostgreSQL/Redis)

## 사전 준비

- **Docker** — 실행·테스트에 필수. 로컬 개발용 PostgreSQL·Redis는 `bootRun`/테스트가 컨테이너로 자동 기동한다.
- JDK는 별도 설치 불필요 — Gradle toolchain(foojay)이 JDK 25를 자동으로 받는다.
- 최초 클론 후 1회: `git config core.hooksPath .githooks` (커밋 훅 활성화)

## 빠른 시작

```bash
./gradlew bootRun          # Docker 필요. compose의 postgres+redis 자동 기동·연결, 앱이 :8080에 뜬다
```

- 헬스: <http://localhost:8080/ping> → `{"data":{"message":"pong"}}`
- **API 문서(Swagger UI)**: <http://localhost:8080/swagger-ui/index.html>

로그인 흐름 확인(로컬 dev seed admin — **프로덕션 전 교체/삭제**):

```bash
curl -X POST localhost:8080/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@swyp.com","password":"swyp-admin-1234"}'
# → { "data": { "accessToken": "...", "refreshToken": "..." } }
```

Swagger UI 우측 상단 **Authorize** 에 access 토큰을 넣으면 보호 엔드포인트도 UI에서 호출된다.

## 테스트 / 빌드

```bash
./gradlew build            # 컴파일 + 전체 테스트(Testcontainers) + 패키징. Docker 필요.
./gradlew test             # 테스트만
```

CI(`.github/workflows/ci.yml`)가 PR·main push마다 동일하게 `./gradlew build`를 돌린다.

## 프로젝트 구조

**Package-by-feature + 레이어 서브패키지** — `com.swyp.backend.<feature>` 아래 존재하는 레이어를 서브패키지로:

```
com.swyp.backend
├── admin/            # 관리자 feature (entity·repository·controller·service·dto)
├── ping/             # walking skeleton (컨벤션 레퍼런스)
└── common/           # 여러 feature가 공유하는 것
    ├── response/     # ApiResponse·ErrorResponse·SuccessCode·ErrorCode
    ├── exception/    # BusinessException·GlobalExceptionHandler
    ├── security/     # JWT·refresh·필터·SecurityConfig 등 인증 인프라
    └── BaseTimeEntity·JpaAuditingConfig
```

- 흐름은 **controller → service → repository** 단방향, 응답은 `ApiResponse` envelope로 통일.
- 스키마 변경은 `src/main/resources/db/migration/`의 Flyway 순차 SQL로만.

## 컨벤션

이 저장소의 개발 규약·아키텍처는 **[`CLAUDE.md`](CLAUDE.md)** 에 있다. 레이어별 상세 작성 규칙은
**`.claude/rules/`** (entity·repository·service·controller·dto·database·testing)에 있고, 로컬 실행 상세는
**`.claude/docs/local-development.md`** 참고.

- 코드·주석·커밋 메시지·테스트 이름: **영어**. 이슈·PR 설명: 한국어 허용.
- 커밋: conventional (`feat`/`fix`/`refactor`/`chore`/`docs`/`test`), 영어. `main` 직접 push/merge는 사람 결정.
- 이슈: GitHub Issues (`SWYP-APP-S6/backend`).
