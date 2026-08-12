---
paths:
  - "src/main/java/**/*Repository.java"
---

# 리포지토리 작성 규칙

- `interface {Feature}Repository extends JpaRepository<{Entity}, {IdType}>`.
- **`repository`만 JPA 영속성 API**(`JpaRepository`/`EntityManager`)에 접근한다. service·controller는 직접 쓰지 않는다.
- 파생 쿼리 메서드 네이밍: `findByEmail` · `existsByEmail` · `findByXAndY` …; 단건은 `Optional<T>` 반환.
- 복잡 쿼리는 `@Query`(JPQL). QueryDSL은 도입 시(지금 없음) — 우선 파생 메서드로 해결.
- **다른 feature의 repository를 직접 호출하지 않는다** — 상대 feature의 `service`를 경유.
- 소프트삭제 엔티티는 `@SQLRestriction`이 살아있는 행만 보이게 하므로, 조회 메서드는 그 전제 위에서 작성.
- 레퍼런스: `com.swyp.backend.admin.AdminRepository`.
