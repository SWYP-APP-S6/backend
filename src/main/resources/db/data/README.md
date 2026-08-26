# db/data — 시드 데이터

Flyway 경로(`db/migration`) **밖**이다. 자동 실행되지 않으며, 필요할 때 `psql` 로 직접 넣는다.
`build.gradle.kts` 의 `processResources` 에서 제외하므로 **jar 에도 들어가지 않는다.**

여기 있는 `*.sql` 은 **커밋하지 않는다**(`.gitignore`). 약 5MB짜리 파생 산출물이고,
아래 스크립트로 언제든 다시 만들 수 있기 때문이다. 이 README 만 추적한다.

## 파일

| 파일 | 내용 |
|---|---|
| `mfds_cookrcp01.sql` | 식약처 조리식품 레시피 DB 1,156건 → `recipes` / `recipe_steps` / `recipe_ingredients` / `ingredients` / `recipe_nutrition` / `recipe_tags` |
| `mfds_cookrcp01_raw.sql` | 원본 API 응답 → `recipe_raw`. 선택 사항이며, 재수집 없이 파서만 고쳐 다시 만들 때 쓴다. 본체를 먼저 넣어야 한다 |

## 재생성

인증키가 프로젝트 루트 `.env` 의 `MFDS_API_KEY` 에 있어야 한다(`.env.example` 참고).

```sh
python3 scripts/mfds_ingest.py
```

## 투입

```sh
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/data/mfds_cookrcp01.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f src/main/resources/db/data/mfds_cookrcp01_raw.sql   # 선택
```

전체가 한 트랜잭션이고 모든 INSERT 가 `on conflict do nothing` 이라 **재실행해도 안전**하다.
자식 행은 id 를 박지 않고 `(source, source_id)` 로 조인해 부모를 찾으므로 identity 컬럼과 충돌하지 않는다.

**운영 서버에 올릴 때**는 커밋 대상이 아니므로, 파일을 직접 전송(`scp`)하거나 서버에서
`MFDS_API_KEY` 를 지정해 스크립트를 돌려 만든다.

## 출처

식품의약품안전처 「조리식품의 레시피 DB」(COOKRCP01) · KOGL 출처표시.
