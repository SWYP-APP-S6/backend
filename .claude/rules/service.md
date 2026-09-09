---
paths:
  - "src/main/java/**/*Service.java"
---

# 서비스 작성 규칙

- 클래스: `@Service @RequiredArgsConstructor @Transactional(readOnly = true)` (+ 필요 시 `@Slf4j`).
- **쓰기 메서드에만** `@Transactional` 을 별도로 붙여 readOnly를 해제한다.
- `@Transactional` 경계는 **service 계층에만** 둔다(controller/repository 아님).
- 조회+존재검증은 `validateAndGet{Entity}(id)` 패턴으로 통일 — 없으면
  `throw new BusinessException({Feature}ErrorCode.{X}_NOT_FOUND)`.
- **엔티티↔DTO 매핑은 여기서** 한다. `@Entity`를 controller로 반환하지 않는다.
- 다른 feature가 필요하면 그 feature의 **service를 주입**해 호출(남의 repository 직접 호출 금지).
- 생성자 주입만(필드 주입 금지): `final` 필드 + `@RequiredArgsConstructor`.
- **현재 시각은 `Clock` 빈을 주입해** 얻는다(`LocalDateTime.now(clock)`/`Instant.now(clock)`) —
  인자 없는 `now()`는 JVM 기본 시간대를 쓰는데 운영 컨테이너는 UTC라 벽시계 값이 9시간 어긋난다
  (로컬은 KST라 테스트로 안 잡힌다). `ClockConfig`가 `Asia/Seoul`로 고정한다.
- 에러 코드는 **이 feature가 소유**한다: `{feature}/exception/{Feature}ErrorCode implements ApiCode`.
