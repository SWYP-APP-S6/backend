---
paths:
  - "src/main/java/**/*Function.java"
---

# function 작성 규칙

- 클래스: `@Component @RequiredArgsConstructor` (생성자 주입만, 필드 주입 금지).
- **자기 feature의 repository만 주입한다.** 다른 feature의 repository·function·service는 주입하지
  않는다 — function은 의존 그래프의 **잎**이어야 순환이 구조적으로 불가능해진다.
- **repository를 부르는 유일한 계층**이다. service·controller는 repository를 직접 보지 않는다.
- **"찾거나 예외"를 여기서 끝낸다**: `get{Entity}ById(id)` → 없으면
  `throw new BusinessException({Feature}ErrorCode.{X}_NOT_FOUND)`. 호출자가 `Optional` 을 다시
  풀게 하지 않는다. "없을 수도 있음"이 정상 흐름이면 `find...`로 `Optional`을 그대로 반환한다.
- 어느 쿼리를 쓸지 고르는 분기(`status == null ? findAll : findByStatus`)는 여기 둔다 — 데이터
  접근 관심사다.
- **`@Transactional`을 붙이지 않는다.** 트랜잭션 경계는 service가 연다.
- **엔티티를 반환한다. DTO를 만들지 않는다** — 엔티티↔DTO 매핑은 service의 일이다.
- 두 feature를 조합하지 않는다. 그건 service가 두 개의 function을 주입해서 한다.
- 레퍼런스: `com.swyp.backend.store.function.StoreFunction`.
