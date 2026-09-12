---
paths:
  - "src/main/java/**/*Service.java"
---

# 서비스 작성 규칙

- 클래스: `@Service @RequiredArgsConstructor @Transactional(readOnly = true)` (+ 필요 시 `@Slf4j`).
- **쓰기 메서드에만** `@Transactional` 을 별도로 붙여 readOnly를 해제한다.
- `@Transactional` 경계는 **service 계층에만** 둔다(controller/function/repository 아님).
- **락을 걸 row는 락 전에 엔티티로 읽지 않는다.** JPA 쿼리는 영속성 컨텍스트에 이미 올라온 엔티티의
  상태를 덮어쓰지 않는다 — 락 없이 먼저 읽어 두면 뒤따르는 `FOR UPDATE`가 row는 잠그지만 객체는
  **락 이전 스냅샷** 그대로다. 상태 검사와 수량 계산이 남의 커밋을 못 봐서 동시 요청 둘이 같은 값을
  읽고 각자 차감하는 **갱신 손실**이 난다 — 락을 걸었으니 안전하다고 착각하기 쉬운 자리다.
  잠글 id가 다른 엔티티에 들어 있으면 **스칼라 프로젝션으로 id만** 꺼낸다(프로젝션은 영속성
  컨텍스트에 아무것도 올리지 않으므로, 엔티티를 락 잡은 뒤 처음 만지게 된다).
  레퍼런스: `HoldExpiryService.expireOverdueHolds`(`OverdueHold` 프로젝션 → 락).
- **여러 row를 잠그는 경로는 모두 같은 순서로 잠근다** — 현재 순서는 **product → hold**
  (`HoldService.create`, `HoldExpiryService`). 한 경로만 뒤집어도 데드락이 난다.
- **외부 API 호출을 트랜잭션 안에 두지 않는다** — 특히 그 트랜잭션이 `FOR UPDATE`를 쥐고 있으면
  타임아웃(`spring.http.clients.read-timeout`)만큼 남의 요청이 그 row에서 대기한다. 커밋 뒤에
  보내야 하는 일(푸시 등)은 상태 컬럼에 적어 두고 배치가 가져간다(`notifications.push_state`).
- **repository를 직접 주입하지 않는다** — 영속성 접근은 자기 feature의 `function`을 거친다.
- 조회+존재검증은 function의 `get{Entity}ById(id)`가 이미 끝낸 상태로 받는다 — service에서
  `Optional`을 다시 풀지 않는다.
- **엔티티↔DTO 매핑은 여기서** 한다. `@Entity`를 controller로 반환하지 않는다.
- **다른 feature가 필요하면 그 feature의 `function`을 주입**한다 — 남의 `service`·`repository`를
  부르지 않는다. service끼리 부르면 순환이 생기고, 그걸 막는 게 function 계층의 존재 이유다.
  두 feature를 합치는 조합 로직은 이 service가 직접 갖는다.
- 생성자 주입만(필드 주입 금지): `final` 필드 + `@RequiredArgsConstructor`.
- **현재 시각은 `Clock` 빈을 주입해** 얻는다(`LocalDateTime.now(clock)`/`Instant.now(clock)`) —
  인자 없는 `now()`는 JVM 기본 시간대를 쓰는데 운영 컨테이너는 UTC라 벽시계 값이 9시간 어긋난다
  (로컬은 KST라 테스트로 안 잡힌다). `ClockConfig`가 `Asia/Seoul`로 고정한다.
- 에러 코드는 **이 feature가 소유**한다: `{feature}/exception/{Feature}ErrorCode implements ApiCode`.
