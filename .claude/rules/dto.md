---
paths:
  - "src/main/java/**/dto/**/*.java"
  - "src/main/java/**/*Request*.java"
  - "src/main/java/**/*Response*.java"
---

# DTO 작성 규칙

- **`record`가 기본** — 요청/응답 및 대부분의 값 객체. Jackson이 record를 네이티브로 직렬화/역직렬화한다.
- `class`는 예외적으로만: **상속**이 필요 / **가변 누적** 조립 / 프레임워크가 no-arg·가변을 요구할 때.
- Request: Bean Validation 애노테이션(`@NotBlank`/`@Size`/…)을 record 컴포넌트에 + 컨트롤러 `@Valid`.
- Response: 엔티티→DTO 변환은 **정적 팩토리 `from({Entity})`** 로.
- `@Entity`를 DTO 대신 노출하지 않는다.
- DTO에 Lombok `@Data`/`@Builder`를 쓰지 않는다 — record로 충분(빌더는 필드 많은 예외 클래스에서만).
- 레퍼런스: `com.swyp.backend.ping.PingResponse`, envelope `com.swyp.backend.common.response.ApiResponse`.
