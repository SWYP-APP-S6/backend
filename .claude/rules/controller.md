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
- 요청 검증: Request DTO에 Bean Validation + 파라미터에 `@Valid`
  (`spring-boot-starter-validation` 도입 시 활성).
- REST 경로: `POST /xxx` · `GET /xxx` · `GET /xxx/{id}` · `PUT|PATCH /xxx/{id}` · `DELETE /xxx/{id}`.
- 요청/응답 타입은 **DTO만** — `@Entity`를 노출하지 않는다.
- 인증 주체 접근(현재 사용자 등)은 auth 도입 후(지금 TBD). 레퍼런스: `com.swyp.backend.ping.PingController`.
