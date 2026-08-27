# db/data — 시드 데이터

Flyway 경로(`db/migration`) **밖**이다. 자동 실행되지 않으며, 필요할 때 `psql` 로 직접 넣는다.
`build.gradle.kts` 의 `processResources` 에서 제외하므로 **jar 에도 들어가지 않는다.**

`mfds_*.sql` 은 **커밋하지 않는다**(`.gitignore`). 약 5MB짜리 파생 산출물이고, 아래 스크립트로
언제든 다시 만들 수 있기 때문이다. 손으로 쓴 시드와 이 README 는 추적한다.

## 파일

| 파일 | 커밋 | 내용 |
|---|---|---|
| `mfds_cookrcp01.sql` | ✗ | 식약처 조리식품 레시피 DB 1,156건 → `recipes` / `recipe_steps` / `recipe_ingredients` / `ingredients` / `recipe_nutrition` / `recipe_tags` |
| `mfds_cookrcp01_raw.sql` | ✗ | 원본 API 응답 → `recipe_raw`. 선택 사항이며, 재수집 없이 파서만 고쳐 다시 만들 때 쓴다. 본체를 먼저 넣어야 한다 |
| `dev_seed_admin.sql` | ✓ | 로컬 개발용 SUPER 관리자. **운영 금지** |

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

**운영 서버에 올릴 때**는 커밋 대상이 아니므로 로컬에서 밀어넣는다:

```sh
./scripts/seed-remote.sh root@api.mangro.cloud              # 본체
./scripts/seed-remote.sh root@api.mangro.cloud --with-raw   # 원본까지
```

파일을 SSH stdin 으로 흘려보내므로 서버에 사본이 남지 않는다. 위 멱등성 덕분에 몇 번을 다시
돌려도 안전하다. 서버에서 직접 만들고 싶다면 `MFDS_API_KEY` 를 지정해 수집 스크립트를 돌린다.

## 관리자 계정

V0001 이 심던 SUPER 관리자는 비밀번호가 공개 저장소에 적혀 있어 **V0012 가 제거**했다.
스키마에 백도어를 두지 않기 위해서다.

**로컬**: `psql "postgresql://swyp:swyp@localhost:5432/swyp" -f dev_seed_admin.sql`
(`admin@swyp.com` / `swyp-admin-1234`)

**운영 첫 관리자**: 관리자 생성 자체가 관리자 권한을 요구하므로 최초 1명은 직접 넣는다.
비밀번호 해시를 만들고(BCrypt, Spring Security 는 `$2a$`/`$2b$`/`$2y$` 를 모두 받는다)

```sh
htpasswd -bnBC 10 "" '실제비밀번호' | tr -d ':\n'
```

그 값으로 한 행만 삽입한다. `dev_seed_admin.sql` 을 운영에 그대로 쓰지 말 것.

```sql
insert into admins (email, name, type, password, created_at, updated_at)
values ('운영자@example.com', '이름', 'SUPER', '$2y$10$...', now(), now());
```

## 출처

식품의약품안전처 「조리식품의 레시피 DB」(COOKRCP01) · KOGL 출처표시.
