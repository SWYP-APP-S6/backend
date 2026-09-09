---
paths:
  - "src/main/java/**/SecurityConfig.java"
  - "src/main/java/**/security/**"
  - "src/main/java/**/*Controller.java"
  - "src/main/java/**/*Auth*.java"
  - "src/test/java/**/*Security*.java"
---

# 인증·인가 작성 규칙

구성: `common.SecurityConfig`(STATELESS·필터 체인) · `common.security.*`(`JwtTokenProvider` ·
`JwtAuthenticationFilter` · `RefreshTokenService` · `JwtProperties` · `RateLimitFilter`) ·
`user.controller.UserAuthController` · `user.service.*`.

## 새 엔드포인트를 `SecurityConfig`에 등록할 때

**빠뜨리면 다른 주체가 통과한다.** 회귀는 `SecurityConfigTest`가 잡는다.

- `/admin/**` → `REALM_ADMIN`.
- 앱 유저 전용 → **`REALM_USER`를 명시**한다. `authenticated()`만 걸면 admin·guest 토큰도 통과하고,
  그 id가 `users`의 **다른 사람**을 가리킨다(`anyRequest()` 기본이 `REALM_USER`라 빠뜨리면 그리 떨어진다).
- 역할이 갈리면 **realm과 role을 둘 다** 요구한다(`AuthorizationManagers.allOf`) — `/owner/**`는
  `REALM_USER`+`ROLE_OWNER`. realm만 걸면 소비자 토큰으로 점주 API를 전부 부를 수 있다.
- **조회 엔드포인트(`/recipes/**`·`/products/nearby` 등)는 `BROWSE_ENDPOINTS`에 등록**해 **GET만**
  GUEST·ADMIN·(`REALM_USER`+`ROLE_CONSUMER`) 셋에 연다 — **점주는 제외된다.** realm만 걸면
  `REALM_USER`가 소비자와 점주를 함께 통과시켜, `/owner/**`와 대칭인 구멍이 반대 방향으로 남는다.
- `permitAll`은 `/ping`과 인증 엔드포인트에만 쓴다(익명 대량 요청의 구멍을 남기지 않기 위해).

## realm

admin과 앱 유저가 같은 JWT/Redis 인프라를 쓰므로 access 토큰에 `realm` 클레임(`TokenRealm` =
ADMIN/USER/GUEST)을 넣고, refresh 토큰도 Redis 키를 `refresh:<realm>:<token>`으로 나눈다(앱 유저
refresh를 `/admin/auth/refresh`에 넣어도 회전되지 않는다). access 토큰은 `typ=access`라 가입
토큰(`typ=signup`)을 bearer로 써도 통과하지 못한다. 인증·인가 거부는 `RestAuthenticationEntryPoint`(401)와
`RestAccessDeniedHandler`(403)가 error envelope로 만든다.

## 카카오 로그인 (앱 유저)

- **소비자 앱과 점주 앱은 별개의 카카오 앱**이다. 앱이 SDK로 받은 access token을 넘기면 서버가
  `/v1/user/access_token_info`로 **app_id가 그 역할의 카카오 앱인지 검증**한 뒤
  (`kakao.{consumer,owner}-app-id`) `/v2/user/me`로 닉네임을 읽는다 — 토큰 치환과, 소비자 앱 토큰을
  점주 엔드포인트에 쓰는 것을 여기서 막는다.
- **카카오 토큰은 저장하지 않는다** — 로그인 순간 신원 확인용으로만 쓴다.
- 엔드포인트도 앱별로 분리해 role을 클라이언트가 정하지 못하게 한다 — `/auth/consumer/kakao`
  ·`/auth/owner/kakao` + `/auth/{signup,refresh,logout}` (`user.controller.UserAuthController`).
- **유저 식별자는 `(oauth_provider, oauth_provider_id, role)`**(V0015 `uq_users_oauth_identity`) —
  카카오 회원번호는 **앱마다 다르게 발급**되므로 서로 다른 두 사람이 같은 번호를 가질 수 있다.
  `role`이 곧 '어느 카카오 앱에서 온 번호인가'다. 그래서 한 사람이 소비자 계정과 점주 계정을 각각
  가질 수 있다(앱이 분리돼 있으니 정상 동작).

## 가입은 2단계

첫 카카오 로그인은 `registered:false` + 단기 **signupToken**만 주고 `users` row를 만들지 않는다.
약관 동의 후 `/auth/signup`이 계정을 만든다(기능명세서 C-002: 인증됐으나 약관 미동의인 계정이 남으면
안 된다 — 이탈하면 아무것도 남지 않는다). 필수 약관 3건은 `SignupRequest`의 `@AssertTrue`로 강제하고,
스키마엔 `terms_agreed_at` 한 건으로 기록한다(항목별 이력이 필요해지면 별도 테이블).

## guest (비회원 구경하기)

`POST /auth/guest`(installId)가 `realm=GUEST`·`role=GUEST` access 토큰을 준다(refresh 없음, TTL은
`JwtProperties.accessTtlFor(realm)`이 realm으로 정한다). principal은 서버가 만든 무작위 long이고
**`users` row가 없다** — `@AuthenticationPrincipal Long`으로 유저를 찾는 엔드포인트에 guest가 닿으면
안 된다. guest가 회원 전용을 부르면 **403 `LOGIN_REQUIRED`**(그 외 거부는 `FORBIDDEN`)가 내려가 앱이
가입 안내로 분기한다. guest 토큰은 인증 수단이 아니라 **rate limit 키**다.

## 과다 요청 방지 2겹

1. nginx `limit_req`(IP 기준, VM 수동 설정 — **적용 예정**).
2. `RateLimitFilter`가 주체(realm+principal) 단위 Redis 고정 창(`ratelimit:<realm>:<id>:<분>`)에서
   `ratelimit.per-minute.*` 한도를 넘기면 **429 `TOO_MANY_REQUESTS`** + `Retry-After`(Redis 장애 시
   fail-open). `/auth/guest`는 installId당 하루 `auth.guest-issue-limit-per-day`회까지만 발급(초과 시
   429 `GUEST_ISSUE_LIMIT_EXCEEDED`).

배경·근거는 `docs/guest-browsing-design.md`(로컬 전용, 저장소에 없음).

## admin (백오피스)

email+password(BCrypt) → JWT access + rotating refresh. `/admin/auth/{login,refresh,logout}`만 public.
역할 = `ROLE_<AdminType>`(SUPER/MANAGER/DEVELOPER). 시드 SUPER admin은 V0001(DEV ONLY, 프로덕션 전 교체).

## 설정

TTL은 `jwt.*`, 가입 토큰은 `auth.signup-ttl`(application.properties), secret은 `JWT_SECRET` env.
카카오 앱 ID·REST 키·클라이언트 시크릿 env는 **미설정 시 동작(fail-closed)까지 `DEPLOY.md` 환경변수
표에 있다** — 여기 중복하지 않는다.
