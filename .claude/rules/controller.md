---
paths:
  - "src/main/java/**/*Controller.java"
---

# 컨트롤러 작성 규칙

- 클래스: `@RestController @RequiredArgsConstructor @RequestMapping("/{feature-plural}")`.
- **얇게 유지**: 라우팅 / 입력 검증 / 응답 래핑만. 비즈니스는 service로 위임(repository 직접 호출 금지).
- 성공 응답은 **`ApiResponse<T>` envelope**로 감싼다: `ApiResponse.of(SuccessCode.OK, data)`.
  상태코드가 필요하면 `ResponseEntity.status(...).body(ApiResponse.of(...))`.
- 에러는 **던지기만** 한다: `throw new BusinessException(ApiCode)`. 응답 포맷/상태 매핑은
  `GlobalExceptionHandler`가 담당(직접 에러 응답 조립 금지).
- 요청 검증: Request DTO에 Bean Validation(`@NotBlank` 등) + 파라미터/바디에 `@Valid`.
  실패는 자동으로 `VALIDATION_FAILED`(fieldErrors) envelope로 매핑된다.
- REST 경로: `POST /xxx` · `GET /xxx` · `GET /xxx/{id}` · `PUT|PATCH /xxx/{id}` · `DELETE /xxx/{id}`.
- 요청/응답 타입은 **DTO만** — `@Entity`를 노출하지 않는다.
- **Swagger 명세는 앱의 코드젠 입력이다.** 앱(Android/Kotlin + Retrofit)이 `/v3/api-docs/app`으로
  클라이언트를 생성하므로 명세가 곧 계약이다. 대부분은 `common/openapi`가 자동 처리하고, 컨트롤러가
  직접 지킬 것은 둘뿐이다:
  - **컨트롤러마다 `@Tag(name = "…")`** — 없으면 태그가 `xxx-controller`가 되고 앱은
    `XxxControllerApi`라는 클래스를 읽게 된다.
  - **메서드명이 곧 `operationId`이고 앱의 메서드명이 된다.** 다른 컨트롤러와 겹치면 springdoc이
    `_1`을 붙이는데 **어느 쪽에 붙는지는 스캔 순서에 달렸다** — 백엔드가 손대지 않은 엔드포인트의
    이름이 바뀔 수 있다. `/admin`과 앱 스펙은 그룹으로 갈라져 있고, 중복은 `OpenApiContractTest`가 막는다.
- 인증 주체 접근(현재 사용자 등)은 auth 도입 후(지금 TBD). 레퍼런스: `com.swyp.backend.ping.PingController`.
