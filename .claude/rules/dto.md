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
- **응답 record 컴포넌트는 기본이 "항상 온다"이다.** `RecordRequiredModelConverter`가 모든 컴포넌트를
  OpenAPI `required`로 올리므로, 코틀린 코드젠에서 `String`(non-null)이 된다. **실제로 null이 될 수
  있는 필드에는 `@Nullable`(JSpecify)을 붙인다** — 안 붙이면 앱이 non-null로 받고 파싱 단계에서
  깨진다. 붙이면 코틀린에서 `String?`이 되어 앱이 분기하게 된다.
- **시간 타입은 `.claude/rules/entity.md`의 구분을 그대로 응답까지 가져간다** — 절대시각은 `Instant`,
  사람이 읽는 벽시계는 `LocalDateTime`. 후자는 오프셋이 없어 OpenAPI `date-time`과 의미가 다르다
  (§`docs/` 참고). 상대시간 문자열("30분 뒤 마감")은 서버가 만들지 않는다 — 절대시각만 주고 앱이 그린다.
- DTO에 Lombok `@Data`/`@Builder`를 쓰지 않는다 — record로 충분(빌더는 필드 많은 예외 클래스에서만).
- 레퍼런스: `com.swyp.backend.ping.PingResponse`, envelope `com.swyp.backend.common.response.ApiResponse`.
