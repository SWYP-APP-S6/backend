# SWYP Backend

SWYP 앱의 백엔드 REST API 서버. (프로덕트 한 줄 설명은 확정되면 여기 채우기.)

이 저장소의 개발 프로세스·컨벤션은 아래를 따른다. **이 지침은 기본 동작보다 우선한다.**

## Stack

- Java 25 (LTS) · Spring Boot 4.1 · Gradle (Kotlin DSL, `build.gradle.kts`)
- Spring Web MVC · Spring Data JPA · Spring Security · PostgreSQL (`org.postgresql:postgresql`)
- JUnit 5 (`./gradlew test`)
- Base package `com.swyp.backend`
- 스키마 마이그레이션: **Flyway** (`spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql`).
  자세한 규약은 아래 Database 섹션.
- **도입 예정 (Phase 2, 코드가 생기면)**: ArchUnit(레이어 경계) · Spotless(포맷) ·
  Checkstyle(스타일). 미리 다 깔지 않는다 — 마찰이 생기면 그때 추가.

## Architecture

팀 합의된 현행 컨벤션. 팀 논의로 바뀌면 규칙 17에 따라 이 파일을 같은 세션에 갱신하고,
(Phase 2 도입 후) ArchUnit 규칙도 함께 맞춘다.

- **Package-by-feature**: `com.swyp.backend.<feature>` (예: `.user`, `.auth`) — 각 feature 안에
  필요에 따라 `controller` / `service` / `repository` / `domain` / `dto`.
- **레이어 경계** (Phase 2에서 ArchUnit이 강제; 지금은 규약):
  - `controller → service → repository` 단방향. controller가 repository를 직접 호출하지 않는다.
  - **`repository`만 JPA 영속성 API**(`JpaRepository`/`EntityManager`)에 접근한다. service·controller는
    엔티티 영속성 API를 직접 쓰지 않는다.
  - feature 간 접근은 상대 feature의 **`service`를 통해서만** — 남의 `repository`를 직접 호출하지 않는다.
  - **`@Entity`는 controller 경계를 넘지 않는다** — 요청/응답은 DTO. 엔티티↔DTO 매핑은 service.
  - `@Transactional`은 **service 계층**에 둔다.
- **레퍼런스 구현**: `com.swyp.backend.ping` (controller→service→dto + `@WebMvcTest` 슬라이스
  테스트)이 이 컨벤션의 walking skeleton이다. 새 feature는 이 형태를 복사해 시작한다.

## Auth

Spring Security 의존성만 있고 설정은 아직 없다(TBD). 인증/인가 모델이 정해지면 여기에 기록한다.

## Workflow (rules)

1. **기본 순서: 논의 → 이슈 등록 → 구현. 코드로 바로 뛰지 않는다.** 문제 정의와 접근을
   충분히 합의한 뒤 GitHub 이슈로 요구사항을 박제하고 구현을 시작한다. 사용자가 "그냥 진행"/
   "just do it"이라고 명시하면 앞단을 건너뛴다.
2. **큰 작업: 이슈 → 계획 → 단계별 실행.** 각 단계 안에서 investigate → 코드 → 테스트 →
   self-review → 커밋을 끝내고 다음으로.
3. **Q&A로 확정한 설계 결정은 구현 전 이슈 본문에 동기화한다** — 이슈가 요구사항 기록.
   대화에만 남은 결정은 잃어버린다.
4. 가장 단순한 동작을 먼저 구현한다. 최적화는 측정 후 별도로 (premature optimization 금지).
5. **`git push` / PR merge는 명시적 사용자 결정이다 — 절대 자동 push 하지 않는다.** (아직 자동
   배포 파이프라인이 없으므로 PR merge가 통합 지점이다.)
6. 코드 변경 후 리뷰한다: `./gradlew build`(컴파일 + 테스트) 통과 → cross-cutting(인증/인가,
   입력 검증, 트랜잭션 경계, 에러 처리, 로깅, 보안) → 코드 품질(타입·중복·네이밍·단일 책임).
7. 리팩터 전, 회귀를 잡을 테스트가 있는지 확인한다. 얇으면 테스트를 먼저 쓴다.
8. 선행 리팩터는 기능과 분리한다: refactor → commit → review → feature.
9. 리뷰 중 발견한 범위 밖 개선은 이슈로 등록하고 접어두지 않는다. **발견마다 지금 고칠지 /
   후속 이슈로 뺄지 판단하고, 이유와 함께 추천한다.** "이슈가 싸니까 전부 이슈로"는 금지 —
   지금 로드된 컨텍스트는 다음 세션엔 없다.
10. 린트/경고는 점진적으로 배수한다. 기존 경고를 작업 중간에 고치지 않는다(본인 diff가
    만든 경고는 커밋 전 수정).
11. **외부 API/프레임워크 기능을 건드리기 전 공식 문서를 확인한다**(Spring, Spring Data JPA,
    Spring Security 등). 기억/추측에 의존하지 않는다.
12. **라이브러리 채택은 런타임 동작 근거로만 정당화한다** — "코드가 줄어듦/DX 좋음"은 근거가
    아니다. 손으로 짠 것과 런타임 동작이 같으면 도입하지 않는다.
13. **버그 픽스는 버그 클래스 제거까지 제안한다** — 회귀 테스트, ArchUnit 규칙, 타입/제약으로
    같은 부류를 원천 차단할 수 있는지. 픽스가 먼저, 예방은 후속(사소하면 같은 세션에).
14. **추상화 정직성**: 패턴/알고리즘 이름을 빌렸으면 런타임 의미가 그 계약과 실제로 일치해야
    한다. 아니면 실제 동작을 서술하는 정직한 이름으로.
15. **주석은 비자명한 WHY만.** 변경 이력·WHAT 재서술 주석 금지. 기본값은 "주석 없음".
16. **완료 판정은 "돌아가는 동작"으로 한다** — 엔드포인트라면 통합 테스트(`@SpringBootTest`/
    `MockMvc`)나 실제 요청(`./gradlew bootRun` + `curl localhost`)으로 확인한다. 컴파일 통과 ≠ 완료.
17. **아키텍처/컨벤션/공유 규약을 바꾸면 이 파일(또는 docs)을 같은 세션에 갱신한다.** 코드가
    문서와 모순된 채 방치되면 완료가 아니다.
18. **구현 중 사고를 짧게 브리핑한다** — 무엇을·왜 바꾸는지, 어떤 트레이드오프를 택하는지 —
    사용자가 중간에 교정할 수 있도록.

## Database

- 스키마 마이그레이션은 **Flyway**로 한다: `src/main/resources/db/migration/`에 순차 SQL 파일.
  - **파일명 = `V0000__<desc>.sql`** — `V` 접두사(versioned 표시; `R__` repeatable·콜백과 구분,
    비우면 Flyway 파싱 버그) + **4자리 zero-pad 정수**(`V0000`, `V0001`, … → `ls` 사전식 정렬이
    실행 순서와 일치) + `__`(더블 언더스코어 구분자; 설명의 snake_case `_`와 충돌 방지).
  - 버전은 **평탄한 정수만** 쓴다(소수점 서브버전 금지). `<desc>`는 영어 snake_case.
- **`ddl-auto=validate`**: Flyway가 스키마 단일 소유자. Hibernate는 엔티티↔스키마 검증만 하고
  절대 스키마를 만들거나 바꾸지 않는다. 엔티티와 마이그레이션이 어긋나면 부팅 실패(테스트가 CI에서 잡음).
- **이미 적용된 마이그레이션 파일은 절대 수정하지 않는다.** 스키마 변경은 항상 새 `V` 파일.
  (Flyway 체크섬이 이를 강제한다.)
- **파괴적 변경(컬럼/테이블 삭제, 타입 축소, NOT NULL 강화)은 expand→contract 2단계로 나눈다.**
  먼저 추가(expand)하고 배포해 구/신 코드가 공존 가능하게 한 뒤, 아무도 안 읽을 때 제거(contract).
  순수 추가 nullable 컬럼은 1단계로 안전.
- 영속성 관심사는 `repository`와 service의 `@Transactional` 경계 안에 가둔다.

## Local development

- **인프라는 Docker Compose로**: `compose.yaml`의 `postgres`(17) + `redis`(7). **Docker 실행 필요.**
- `./gradlew bootRun` — `spring-boot-docker-compose`(developmentOnly)가 compose의 postgres+redis를
  **자동 기동·연결**(ServiceConnection). 수동 DB 설치 불필요. 연결 설정을 손으로 적지 않는다.
- **전체 스택을 도커로**: `docker compose --profile app up` — postgres+redis+앱(멀티스테이지
  `Dockerfile`, temurin 25). `app`은 profile이 걸려 기본 기동/자동관리에서 제외된다(순환 방지);
  앱 컨테이너는 서비스명(`postgres`/`redis`)으로 env 연결한다.
- Redis 클라이언트는 `spring-boot-starter-data-redis`, 속성 접두는 `spring.data.redis.*`.

## Tests

- JUnit 5. 테스트는 **Testcontainers로 실제 PostgreSQL**에 대해 실행한다 —
  `TestcontainersConfiguration`의 `@ServiceConnection` PostgreSQL 컨테이너를 `@SpringBootTest`가 부팅 시
  띄운다(H2 방언 불일치 회피). **테스트 실행에 Docker가 필요**하다.
- `./gradlew build` — 컴파일 + 테스트 + 패키징 (로컬 게이트, 규칙 6).
- `./gradlew bootTestRun` — 앱을 임시 PostgreSQL 컨테이너로 로컬 실행(`TestBackendApplication`). 실제
  PostgreSQL 미설치 상태로 굴려볼 때.
- **CI** (`.github/workflows/ci.yml`): PR·main push마다 `./gradlew build`. Docker가 있는 ubuntu
  러너에서 Testcontainers가 동작한다.

## Issue management

- GitHub Issues (`SWYP-APP-S6/backend`).
- Labels: `enhancement`(기능) · `bug` · `refactor` · `tech-debt` · `test`.
- **제목**: 변경을 서술하는 한국어 명사구(영문 기술용어는 그대로). `[tag]`/`Domain:` 접두사 금지.
  이슈 번호 교차참조는 제목이 아니라 본문에 `관련: #N`.
- 이슈/PR **본문**은 한국어 허용(내부 팀 대상).

## Language policy

- **코드 / 주석 / 커밋 메시지 / 테스트 이름: 영어.**
- 이슈 / PR 설명: 한국어 허용.

## Commit convention

- 커밋 메시지는 **영어**, conventional (`feat`/`fix`/`refactor`/`chore`/`docs`/`test`). 본문은
  ~72자에서 hard-wrap. 이슈 참조는 `Closes #N` / `Refs #N`.
- **AI/Claude 트레일러(`Co-Authored-By`, `Reviewed-by` 등)를 넣지 않는다** — 커밋은 단독 작성
  (HHsungmoon). 이 저장소의 git 정체성은 개인 계정(로컬 `git config`에 설정됨). 이 규칙은
  **`.githooks/commit-msg` 훅이 기계적으로 강제**한다(AI 트레일러 자동 제거; 인간 co-author는
  유지). 신규 clone은 `git config core.hooksPath .githooks`를 한 번 실행해 활성화한다.
- 변경이 여러 관심사를 걸치면 의미 단위(기능/버그/리팩터)로 커밋을 분리한다.
- 배포가 필요 없는 변경(문서·설정)은 제목에 `[skip ci]` 접두(향후 CI 도입 시).

## Harness

`.claude/`에 개발 워크플로가 슬래시커맨드로 들어있다: `/go`(이슈·작업 end-to-end),
`/issue`(이슈 등록), `/code-review`(리뷰), `/pr`(PR 올리기). 권한·훅 근거는
[.claude/SETTINGS.md](.claude/SETTINGS.md). 워크트리·배포·정적분석 가드는 필요해지면 추가한다.
