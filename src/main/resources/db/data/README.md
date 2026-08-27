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
| `team_admins.tsv` | ✗ | 관리자 계정 명단(이메일·이름·타입·전화번호). 개인정보이고 이 저장소는 public 이라 `.gitignore` 한다 |

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

## 관리자 계정

V0001 이 심던 SUPER 관리자는 비밀번호가 공개 저장소에 적혀 있어 **V0012 가 제거**했다.
스키마에 백도어를 두지 않기 위해서다.

**로컬**: `psql "postgresql://swyp:swyp@localhost:5432/swyp" -f dev_seed_admin.sql`
(`admin@swyp.com` / `swyp-admin-1234`)

**운영 관리자**: 관리자 생성 자체가 관리자 권한을 요구하므로 최초 계정들은 직접 넣는다.
`scripts/create_admins.sh` 가 명단(TSV)을 받아 INSERT 문을 만든다.

```sh
# 로컬
./scripts/create_admins.sh < src/main/resources/db/data/team_admins.tsv \
  | psql "$DATABASE_URL" -v ON_ERROR_STOP=1

# 운영 (SSH stdin 으로 흘려보내 서버에 사본을 남기지 않는다)
./scripts/create_admins.sh < src/main/resources/db/data/team_admins.tsv \
  | ssh root@api.mangro.cloud 'docker exec -i backend-postgres-1 psql -U swyp -d swyp -v ON_ERROR_STOP=1'
```

명단은 탭 구분 4열 — `email<TAB>name<TAB>SUPER|MANAGER|DEVELOPER<TAB>phone`.

**초기 비밀번호는 전화번호에서 숫자만 남긴 값**이다. 팀 전원이 이미 아는 값이라 서로의 계정에
로그인할 수 있으므로, 각자 첫 로그인 후 `PUT /admin/auth/password` 로 반드시 바꿔야 한다.
재실행해도 기존 행은 건드리지 않는다(`--reset-passwords` 를 줄 때만 갱신) — 이미 바꾼 사람이
조용히 전화번호로 되돌아가면 본인만 모르는 채 계정이 열린다.

`dev_seed_admin.sql` 은 비밀번호가 저장소에 적혀 있으므로 **운영에 쓰지 않는다.**

```sql
insert into admins (email, name, type, password, created_at, updated_at)
values ('운영자@example.com', '이름', 'SUPER', '$2y$10$...', now(), now());
```

## 출처

식품의약품안전처 「조리식품의 레시피 DB」(COOKRCP01) · KOGL 출처표시.
