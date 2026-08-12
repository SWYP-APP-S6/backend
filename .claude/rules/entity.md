---
paths:
  - "src/main/java/**/domain/**/*.java"
  - "src/main/java/**/entity/**/*.java"
---

# 엔티티 작성 규칙

- `extends BaseTimeEntity` — `created_at`/`updated_at`는 JPA Auditing이 채운다(직접 세팅 X).
- Lombok: **`@Getter` + `@NoArgsConstructor(access = PROTECTED)` 만.** 금지: `@Data` · 전면 `@Setter` ·
  기본 `@EqualsAndHashCode`(양방향 무한재귀·lazy 트리거·가변 hashCode 버그원).
- PK: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` (bigint identity).
- 연관관계: `@ManyToOne(fetch = FetchType.LAZY)` — **EAGER 금지**(기본 LAZY).
- Enum 컬럼: `@Enumerated(EnumType.STRING)`; DB `CHECK` 제약은 enum **이름(대문자)** 과 일치시킨다.
- **상태 변경은 setter 금지 → 도메인 메서드**로 표현.
- `@Builder`를 쓰면 초기화 컬렉션/기본값 필드에 **`@Builder.Default` 필수**(없으면 null).
- 시간 타입: 절대·기록·계산된 순간 = `Instant`+`timestamptz`, 사람 벽시계 = `LocalDateTime`+`timestamp`.
- 소프트삭제가 필요하면: `deleted_at` nullable + `@SQLDelete` + `@SQLRestriction("deleted_at is null")`,
  유니크 컬럼은 부분 유니크 인덱스(`where deleted_at is null`).
- `@Entity`는 controller 경계를 넘지 않는다 — 요청/응답은 DTO(매핑은 service).
- 스키마는 Flyway 마이그레이션으로만; `ddl-auto=validate`가 엔티티↔스키마 불일치를 부팅 시 잡는다.
- 레퍼런스: `com.swyp.backend.admin.Admin`, 공용 base `com.swyp.backend.common.BaseTimeEntity`.
