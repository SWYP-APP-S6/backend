# SWYP Backend

## Stack

- Java 25 (LTS) · Spring Boot 4.1 · Gradle (Kotlin DSL, `build.gradle.kts`) · PostgreSQL · Redis
- Spring Web MVC · Spring Data JPA · Spring Security · Flyway · JUnit 5 (`./gradlew test`)
- Base package `com.swyp.backend`
- **Lombok** — 엔티티 보일러플레이트 한정(규칙 12의 명시적 예외). 상세 규칙은
  `.claude/rules/{entity,dto}.md`.
- **외부 API 호출**: `RestClient`. **`spring-boot-starter-restclient`가 있어야** `RestClient.Builder`
  빈이 생긴다(Boot 4는 클라이언트 자동설정이 별도 모듈 — webmvc 스타터만으론 없다).
  타임아웃은 `spring.http.clients.{connect,read}-timeout`으로 전역 설정.
- **Jackson 3**(`tools.jackson.databind`)만 import한다 — 2.x(`com.fasterxml.jackson`)도 전이
  의존성으로 함께 있어 잘못 써도 컴파일은 되고, 그 경우 `JsonNode`가 트리가 아니라
  **POJO로 직렬화**돼(`{"array":false,…}`) 응답이 조용히 망가진다.
- **API 문서 = 앱의 코드젠 입력**: springdoc-openapi(`/swagger-ui`). **Boot 4 → springdoc 3.x.**
  스펙은 그룹으로 갈라져 있다 — **`/v3/api-docs/app`(앱)**, `/v3/api-docs/admin`(백오피스). 앱이 이
  스펙으로 Retrofit 클라이언트를 생성하므로 명세가 곧 계약이고, 규약은 `common/openapi`가 강제한다:
  응답 record 컴포넌트는 `required`로 올라가고(실제 null 가능 필드에만 `@Nullable`), 모든 오퍼레이션에
  `ErrorResponse` 기반 4xx·5xx가 붙으며, 컨트롤러는 `@Tag`를 단다. 회귀는 `OpenApiContractTest`가 잡는다.
  인증은 `bearerAuth` 스킴.
- **Package-by-feature + 레이어 서브패키지**: `com.swyp.backend.<feature>`(예: `.admin`, `.ping`) 아래
  `controller` / `service` / `function` / `repository` / `entity` / `dto`. `com.swyp.backend.common`에는
  **여러 feature가 공유하는 타입만** 둔다(`BaseTimeEntity`, `JpaAuditingConfig` 등).
- **화면 조합 feature**: 여러 feature를 한 화면으로 모으는 응답은 **자기 feature를 갖는다**
  (`com.swyp.backend.home` = 점주 홈 `GET /owner/home`). entity·repository 없이 controller/service/dto만
  두고 데이터는 각 feature의 `function`에서 받는다 — 상품 API가 상점·찜·알림까지 내주기 시작하면
  그 엔드포인트의 이름이 거짓말이 된다(규칙 14).
- **`function` = 그 feature가 남에게 내주는 재사용 단위.** repository 호출과 "찾거나 예외"를 여기서
  끝내고(`get{Entity}ById` → 없으면 `BusinessException`), service는 트랜잭션·유스케이스 조합·DTO
  매핑만 맡는다. 파사드를 위에 얹는 대신 공용 계층을 **아래**에 둔 것 — 파사드는 service끼리의
  호출을 막지 못하지만, function은 애초에 부를 것이 repository밖에 없다.
- **service는 "무엇을 하는가"만 읽히게 둔다.** 조회의 준비물(bounding box, 기준 시각)과 DB가 대신
  못 해주는 조회 연산(반경 필터 등)은 function이 가져가고, service에는 유스케이스 조합·정렬·응답
  조립만 남는다. function은 **의도 단위 파라미터**를 받고 **엔티티 또는 읽기 모델**을 돌려준다 —
  응답 DTO를 만들면 그 function을 쓰는 모든 feature가 남의 API 계약에 묶인다.
- **레이어 경계** — `ArchitectureTest`(ArchUnit)가 빌드에서 강제한다:
  - `controller → service → function → repository` 단방향. 한 칸씩만 내려간다.
  - **`repository`만 JPA 영속성 API**(`JpaRepository`/`EntityManager`)에 접근하고, repository를 부르는
    건 **`function`뿐**이다.
  - **feature 간 접근은 상대 feature의 `function`을 통해서만** — 남의 `service`·`repository`는 부르지
    않는다. service끼리 부르기 시작하면 사슬이 생기고 반대 방향 호출 하나로 순환이 된다.
    **`function`은 잎(leaf)이라 순환이 구조적으로 불가능하다.**
  - **`function`은 다른 `function`도, 어떤 `service`도 부르지 않는다.** 두 feature를 조합하는 건
    service의 일이다.
  - **`@Entity`는 controller 경계를 넘지 않는다** — 요청/응답은 DTO(기본 `record`), 매핑은 service.
  - `@Transactional`은 **service 계층**에 둔다 — function은 트랜잭션 경계를 열지 않는다.
- **API 응답은 표준 envelope**: 성공 `ApiResponse<T>{status,code,message,data}`(컨트롤러가 감싼다),
  실패 `ErrorResponse{status,code,message,fieldErrors}`(`GlobalExceptionHandler`가 생성 —
  `ResponseEntityExceptionHandler`를 상속해 프레임워크
  클라이언트 에러 405·400·415도 포함). 비즈니스 예외는 **`BusinessException(ApiCode)` 하나**로 던지고,
  **에러 코드는 각 feature가 자기 enum(`implements ApiCode`)에 소유**한다(제네릭만
  `common.response.ErrorCode` — global→feature 역결합 회피).
- **제약 메시지는 `src/main/resources/ValidationMessages.properties`가 소유한다** — 없으면 Hibernate
  Validator 기본 번들이 **JVM 로케일에 따라** 골라져 로컬(ko)은 한국어, 운영 컨테이너
  (`eclipse-temurin`의 `LANG=en_US.UTF-8`)는 영어가 나간다. 이 번들은 로케일 접미사가 없어 모든
  환경에서 이긴다. 제약별 문구가 따로 필요하면 애노테이션의 `message`가 우선한다.
- `createdAt`/`updatedAt`은 Spring Data JPA Auditing이 채운다 — DB default/trigger가 아니므로 **쓰기가
  JPA를 거쳐야** 채워진다(현재 앱이 유일 writer).
- **레퍼런스 구현**: `com.swyp.backend.ping`(controller→service→dto + 슬라이스 테스트)이 walking
  skeleton이다. 새 feature는 이 형태를 복사해 시작한다.

## Auth

- 주체는 셋이다 — **admin**(백오피스, email+password), **앱 유저**(소비자·점주, **카카오 로그인만**),
  **guest**(비회원 구경, `users` row 없음). 셋 다 JWT access + `realm` 클레임(`TokenRealm` =
  ADMIN/USER/GUEST)을 쓰고, refresh(admin·앱 유저)는 Redis에 저장·회전한다.
- 소비자 앱과 점주 앱은 **별개의 카카오 앱**이라 회원번호도 앱마다 다르게 발급된다 — 그래서 유저
  식별자가 `(oauth_provider, oauth_provider_id, role)`이고, 한 사람이 소비자 계정과 점주 계정을
  각각 가질 수 있다.
- **로컬 테스트 토큰**: `POST /dev/test-token`이 카카오 왕복 없이 소비자/점주 access 토큰을 준다
  (admin-web `/hold-test`가 앱 전용 API를 호출하는 데 쓴다). 계정은
  `(oauth_provider='dev', 'test-consumer'|'test-owner')` 한 건을 재사용한다. `dev.test-token.enabled`
  스위치를 **운영 프로파일이 false로 덮어쓰고**, 그러면 컨트롤러 빈 자체가 만들어지지 않는다 —
  `SecurityConfig`의 permitAll도 같은 스위치를 보므로 규칙과 엔드포인트가 어긋날 수 없다.
- **새 엔드포인트를 만들면 `SecurityConfig`에 realm과 role을 함께 등록해야 한다** — 빠뜨리면 다른
  주체가 통과한다. 등록 규칙·카카오 검증·가입 2단계·guest·rate limit 상세는
  **`.claude/rules/security.md`**(보안·컨트롤러 파일 작성 시 자동 로드), env 변수는
  [`DEPLOY.md`](DEPLOY.md).

## Workflow (rules)

1. **논의 후 바로 구현한다** — 문제 정의와 접근을 합의한 뒤 코드로 들어간다. 이 규모에선 GitHub
   이슈 트래킹을 쓰지 않는다.
2. **큰 작업은 계획 → 단계별 실행.** 각 단계에서 investigate → 코드 → 테스트 → self-review까지
   끝내고 다음으로 (커밋은 제외 — 규칙 5).
3. **검증 강도는 변경의 리스크에 맞춘다** — 런타임 동작이 안 바뀌는 변경(문서·설정·주석)은 컴파일
   체크로 충분하다. 전체 빌드/테스트/curl은 동작이 바뀔 때만, 매번 같은 강도로 반복하지 않는다.
4. 가장 단순한 동작을 먼저 구현한다. 최적화는 측정 후 별도로(premature optimization 금지).
5. **커밋 / `git push` / PR 생성·merge는 사용자의 명시적 요청이 있을 때만 한다.** 구현·테스트·리뷰가
   끝났다고 자동으로 커밋하지 않는다 — 코드 생성과 "커밋 → 푸시 → PR"은 완전히 별개 단계다.
6. 코드 변경 후 리뷰한다: `./gradlew build`(컴파일+테스트 — Testcontainers가 실제 PostgreSQL을
   띄우므로 **Docker 필요**) 통과 → cross-cutting(인증/인가, 입력 검증, 트랜잭션 경계, 에러 처리,
   로깅, 보안) → 코드 품질(타입·중복·네이밍·단일 책임). CI가 PR·main push마다 같은 `build`를 돌린다.
7. 리팩터 전, 회귀를 잡을 테스트가 있는지 확인한다. 얇으면 테스트를 먼저 쓴다.
8. 선행 리팩터는 기능과 분리한다 — refactor → review → feature로 쪼갤 수 있게 작업한다.
9. **리뷰 중 발견한 범위 밖 개선은 묻어두지 않는다** — 발견마다 지금 고칠지/넘어갈지 판단하고 이유와
   함께 알린다. 지금 로드된 컨텍스트는 다음 세션엔 없으니 사소해도 언급 없이 넘어가지 않는다.
10. 린트/경고는 점진적으로 배수한다 — 기존 경고는 작업 중간에 건드리지 않고, 본인 diff가 만든
    경고만 고친다.
11. **외부 API/프레임워크를 건드리기 전 공식 문서를 확인한다**(Spring · Spring Data JPA ·
    Spring Security 등). 기억이나 추측에 의존하지 않는다.
12. **라이브러리 채택은 런타임 동작 근거로만 정당화한다** — "코드가 줄어듦/DX 좋음"은 근거가 아니다.
    손으로 짠 것과 런타임 동작이 같으면 도입하지 않는다. (예외: Lombok — 런타임 의존성이 아니라
    컴파일타임 코드생성기라 이 규칙의 대상이 아니며, 팀 도입 결정 2026-08-12.)
13. **버그 픽스는 버그 클래스 제거까지 제안한다** — 회귀 테스트·ArchUnit 규칙·타입/제약으로 같은
    부류를 원천 차단할 수 있는지. 픽스가 먼저, 예방은 후속(사소하면 같은 세션에).
14. **추상화 정직성** — 패턴/알고리즘 이름을 빌렸으면 런타임 의미가 그 계약과 일치해야 한다.
    아니면 실제 동작을 서술하는 정직한 이름으로.
15. **Java 코드에는 주석을 달지 않는다**(Javadoc·클래스/메서드 설명 포함). 맥락·이유가 필요하면
    커밋 메시지나 PR 본문에 남긴다.
16. **완료 판정은 "돌아가는 동작"으로 한다** — 엔드포인트라면 통합 테스트(`@SpringBootTest`/`MockMvc`)나
    실제 요청(`./gradlew bootRun` + `curl`)으로 확인한다. 컴파일 통과 ≠ 완료.
17. **아키텍처/컨벤션/공유 규약을 바꾸면 이 파일을 같은 세션에 갱신한다.** 코드가 문서와 모순된 채
    방치되면 완료가 아니다.
18. **구현 중 사고를 짧게 브리핑한다** — 무엇을·왜 바꾸는지, 어떤 트레이드오프를 택하는지.
    사용자가 중간에 교정할 수 있도록.

## Database

- **스키마는 Flyway 마이그레이션으로만 바꾼다** — `ddl-auto=validate`라 엔티티만 고치면 DDL이 나가지
  않고 부팅이 실패한다. 작성 규칙은 `.claude/rules/database.md`.
- **앱 타임존은 `Asia/Seoul`**, 현재 시각은 **`Clock` 빈을 주입해** 얻는다 — 인자 없는 `now()`는 운영
  컨테이너의 UTC를 따라 9시간 어긋난다(로컬은 KST라 테스트로 안 잡힌다). 시간 타입·소프트 삭제와
  함께 `.claude/rules/entity.md`.

## Deployment

- **NCP 단일 VM + `docker compose`**(app/postgres/redis) + 앞단 nginx. 배포는 **서버가 끌어온다** —
  `ssh deploy@<서버>` → `cd ~/backend && ./scripts/deploy.sh`. 절차·필수 env·운영 명령어·메모리
  배분은 [`DEPLOY.md`](DEPLOY.md), 서버 `.env` 템플릿은 `deploy.env.example`.
- **GitHub Actions로 배포하지 않는다** — 저장소가 public이라 self-hosted runner를 붙이면 fork의 PR이
  배포 호스트에서 코드를 실행할 수 있다(근거는 DEPLOY.md). CI의 `build` job은 PR·push마다 계속 돈다.
- `compose.yaml`은 **로컬과 운영이 같은 파일**이다 — 운영 전용 값은 서버 `.env`로만 주입하고, 파일에
  적힌 기본값은 전부 로컬용이다.

## Language policy

- **코드 / 커밋 메시지 / 테스트 이름: 영어.** PR 설명은 한국어 허용.

## Commit convention

- 커밋 메시지는 **영어**, conventional (`feat`/`fix`/`refactor`/`chore`/`docs`/`test`). 본문은
  ~72자에서 hard-wrap.
- **AI/Claude 트레일러(`Co-Authored-By`, `Reviewed-by` 등)를 넣지 않는다** — 커밋은 단독 작성.
  `.githooks/commit-msg` 훅이 기계적으로 강제한다(AI 트레일러 자동 제거, 인간 co-author는 유지).
  신규 clone은 `git config core.hooksPath .githooks`를 한 번 실행해 활성화한다.
- 변경이 여러 관심사를 걸치면 의미 단위(기능/버그/리팩터)로 커밋을 분리한다.
- **테스트 코드는 별도 커밋으로 쌓는다** — 기능 커밋과 테스트 커밋을 나눠, 리뷰어가 "무엇을
  바꿨나"와 "무엇으로 지켰나"를 따로 읽게 한다.
- **마이그레이션은 그 PR의 첫 커밋에 단독으로 둔다** — 스키마 변경을 한 곳에 모아 리뷰 대상을
  분명히 하되, **뒤가 아니라 앞에** 놓는다. 뒤에 두면 그 앞 커밋들의 엔티티가 아직 없는 컬럼을
  가리켜 `ddl-auto=validate`가 기동을 막고, `main`이 merge commit 방식이라 그 커밋들이 히스토리에
  영구히 남아 `git bisect`가 무관한 회귀를 쫓을 때 통째로 걸린다. 앞에 두면 순수 추가·nullable
  스키마는 구 코드와 공존하므로(`.claude/rules/database.md`의 expand→contract) 모든 커밋이 기동한다.
- 배포가 필요 없는 변경(문서·설정)은 제목에 `[skip ci]` 접두.

## Harness

**이 파일엔 "항상 참인 것"만 둔다.** 특정 레이어·파일에서만 필요한 규칙은 `rules/`로, 특정 절차에서만
필요한 건 `commands/`로 보낸다(규칙 17에 따라 같은 세션에 갱신).

- **`.claude/rules/`** — `paths:` 매칭 파일을 작성/수정할 때 **자동 로드**:
  `entity` · `repository` · `service` · `controller` · `dto` · `security` · `database` · `testing`.
- **`.claude/commands/`** — 슬래시 커맨드로 **호출할 때만** 로드: `/go`(분석→구현→테스트→리뷰) ·
  `/code-review` · `/pr` · `/issue`.
- 로컬 실행·인프라(Docker Compose, `bootRun`, `bootTestRun`)는 `.claude/docs/local-development.md`
  (**로컬 실행엔 Docker 필요**), 권한·훅 근거는 [.claude/SETTINGS.md](.claude/SETTINGS.md).
