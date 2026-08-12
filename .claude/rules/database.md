---
paths:
  - "src/main/resources/db/migration/**"
---

# 마이그레이션(Flyway) 작성 규칙

- 스키마 변경은 **Flyway 순차 SQL**로만: `src/main/resources/db/migration/`.
- **파일명 = `V0000__<desc>.sql`** — `V` 접두사(versioned; `R__` repeatable·콜백과 구분, 비우면 파싱 버그)
  + **4자리 zero-pad 정수**(`V0000`·`V0001`… → `ls` 사전식 정렬 = 실행 순서) + `__`(더블 언더스코어
  구분자; 설명의 snake_case `_`와 충돌 방지). 버전은 **평탄한 정수만**, `<desc>`는 영어 snake_case.
- **이미 적용된 마이그레이션 파일은 절대 수정하지 않는다** — 변경은 항상 새 `V` 파일(Flyway 체크섬이 강제).
- **파괴적 변경(컬럼/테이블 삭제·타입 축소·NOT NULL 강화)은 expand→contract 2단계**: 먼저 추가·배포로
  구/신 코드 공존 → 아무도 안 읽을 때 제거. 순수 추가 nullable 컬럼은 1단계로 안전.
- **`ddl-auto=validate`**: Flyway가 스키마 단일 소유자, Hibernate는 엔티티↔스키마 검증만. 어긋나면
  부팅 실패(테스트/CI가 잡음). 컬럼 타입은 엔티티 필드와 일치시킨다 — **시간 타입 규칙은 `entity.md` 참조**.
