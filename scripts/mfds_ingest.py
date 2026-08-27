"""식약처 조리식품 레시피 DB(COOKRCP01) → 시드 SQL 생성기.

앱 코드가 아니라 데이터 준비 도구다. 운영 서버에는 결과물인 SQL 파일만 들어가고,
이 스크립트는 재생성이 필요할 때만 돌린다.

    python3 scripts/mfds_ingest.py              # API 에서 새로 받아 생성
    python3 scripts/mfds_ingest.py --cache x.json   # 받아둔 스냅샷으로 생성

인증키는 프로젝트 루트 `.env` 의 MFDS_API_KEY 에서 읽는다.
출력: src/main/resources/db/data/{mfds_cookrcp01.sql, mfds_cookrcp01_raw.sql}
"""

from __future__ import annotations  # macOS 기본 python 3.9 에서도 `str | None` 표기를 쓰기 위해

import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "src" / "main" / "resources" / "db" / "data"
SERVICE = "COOKRCP01"
PAGE = 1000
CHUNK = 400  # INSERT 한 문장에 담을 VALUES 행 수

# 재료명 앞에 붙는 손질 표현. 공백으로 끊긴 토큰만 제거한다 —
# '생강'의 '생'처럼 접두가 아닌 글자를 잘라내지 않기 위해서다.
PREP_WORDS = {
    "다진", "채썬", "채", "썬", "잘게", "굵게", "곱게", "얇게", "어슷", "저민",
    "손질한", "삶은", "데친", "불린", "볶은", "구운", "말린", "씻은", "다듬은", "갈은",
}
GROUP_WORDS = (
    "재료", "육수", "양념", "양념장", "소스", "고명", "장식", "주재료", "부재료",
    "반죽", "곁들임", "드레싱", "국물", "육수용", "속재료", "밑간",
)
UNIT = (  # 긴 표기를 먼저 둬야 'Ts' 가 'T' 로 잘리지 않는다
    "Ts|ts|g|ml|kg|mg|L|l|cc|개|장|큰술|작은술|컵|줄기|마리|쪽|모|봉지|알|대|송이|"
    "자밤|줌|공기|판|캔|팩|병|포기|단|톨|뿌리|숟가락|티스푼|테이블스푼|cm|T|t"
)
MARKERS = r"●•·※◆▶\-"  # 문자 클래스에 그대로 끼워 넣으므로 하이픈은 이스케이프해 둔다
VULGAR = {"⅓": 1 / 3, "½": 0.5, "¼": 0.25, "⅔": 2 / 3, "¾": 0.75, "⅛": 0.125, "⅕": 0.2, "⅖": 0.4}


# ---------------------------------------------------------------- fetch


def read_api_key() -> str:
    env = ROOT / ".env"
    if not env.exists():
        sys.exit("`.env` 가 없습니다. .env.example 를 복사해 MFDS_API_KEY 를 채우세요.")
    for line in env.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            if k.strip() == "MFDS_API_KEY":
                key = v.strip().strip('"').strip("'")
                if key:
                    return key
    sys.exit("`.env` 의 MFDS_API_KEY 가 비어 있습니다.")


def fetch_all() -> list[dict]:
    key, rows, start = read_api_key(), [], 1
    while True:
        url = f"http://openapi.foodsafetykorea.go.kr/api/{key}/{SERVICE}/json/{start}/{start + PAGE - 1}"
        try:
            with urllib.request.urlopen(url, timeout=60) as resp:
                body = json.loads(resp.read().decode("utf-8"))[SERVICE]
        except Exception as exc:  # noqa: BLE001 - 키가 메시지에 섞이지 않도록 타입만 노출
            sys.exit(f"요청 실패: {type(exc).__name__}")
        code = body.get("RESULT", {}).get("CODE")
        if code != "INFO-000":
            sys.exit(f"API 오류: {code} {body.get('RESULT', {}).get('MSG')}")
        batch = body.get("row", [])
        rows.extend(batch)
        total = int(body["total_count"])
        print(f"  {start}~{start + PAGE - 1}: {len(batch)}행 (누적 {len(rows)}/{total})")
        if not batch or len(rows) >= total:
            return rows
        start += PAGE


# ---------------------------------------------------------------- parse helpers


def clean(v) -> str:
    return (v or "").strip()


def split_top_level(text: str) -> list[str]:
    """괄호 밖의 쉼표에서만 자른다. 원본에 짝이 안 맞는 괄호가 있어 깊이는 0 밑으로 내리지 않는다."""
    out, buf, depth = [], [], 0
    for ch in text:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth = max(0, depth - 1)
        if ch == "," and depth == 0:
            out.append("".join(buf))
            buf = []
        else:
            buf.append(ch)
    out.append("".join(buf))
    return [t.strip() for t in out if t.strip()]


def to_number(raw: str):
    raw = raw.strip()
    for glyph, val in VULGAR.items():
        if glyph in raw:
            head = raw.replace(glyph, "").strip()
            base = float(head) if re.fullmatch(r"\d+(\.\d+)?", head) else 0.0
            return round(base + val, 2)
    m = re.fullmatch(r"(\d+)\s*/\s*(\d+)", raw)
    if m and int(m.group(2)):
        return round(int(m.group(1)) / int(m.group(2)), 2)
    if re.fullmatch(r"\d+(\.\d+)?", raw):
        return round(float(raw), 2)
    return None


def parse_ingredient(token: str):
    """(name, amount, unit) — 이 데이터셋의 두 가지 수량 표기를 모두 다룬다.

    형식 1: '연두부 75g(3/4모)'   이름 뒤에 수량+단위
    형식 2: '닭고기(가슴살, 120g)' 괄호 안에 수량+단위
    어느 쪽도 아니면 수량/단위는 None (호출부가 raw_text 로 표시한다).
    """
    text = token.strip()
    m = re.match(rf"^(?P<name>.+?)\s*(?P<amt>\d+(?:\.\d+)?|\d+\s*/\s*\d+)\s*(?P<unit>{UNIT})(?![A-Za-z가-힣])", text)
    if m:
        return normalize_name(m.group("name")), to_number(m.group("amt")), m.group("unit")
    if "(" in text:
        num = rf"(\d+(?:\.\d+)?|\d+\s*/\s*\d+|[{''.join(VULGAR)}])"
        inside = text[text.index("(") + 1 :]
        found = re.findall(rf"{num}\s*({UNIT})(?![A-Za-z가-힣])", inside)
        if found:
            # '사과(1/4개=60g)' 처럼 여러 개면 무게 표기를 우선한다
            amt, unit = next((p for p in found if p[1] in ("g", "ml", "kg")), found[0])
            return normalize_name(text[: text.index("(")]), to_number(amt), unit
    # '소금 0.2' 처럼 단위 없이 수량만 붙는 표기
    bare = re.match(r"^(?P<name>.+?)\s+(?P<amt>\d+(?:\.\d+)?|\d+\s*/\s*\d+)$", text)
    if bare:
        return normalize_name(bare.group("name")), to_number(bare.group("amt")), None
    return normalize_name(re.sub(r"\(.*?\)?$", "", text)), None, None


def normalize_name(name: str) -> str:
    name = re.sub(r"\(.*?\)?", "", name)  # 괄호 주석 제거(짝 안 맞아도 동작)
    name = re.sub(rf"^[\[\]{MARKERS}\s]+", "", name)
    name = re.sub(r"[\[\]]", "", name)
    name = re.sub(r"[①②③④⑤⑥⑦⑧⑨⑩]", "", name).strip()  # 같은 재료 반복 시 붙는 첨자
    parts = name.split()
    while parts and parts[0] in PREP_WORDS:
        parts.pop(0)
    return " ".join(parts).strip(" .,·")[:64]


def norm_key_of(name: str) -> str:
    # 한글이 하나도 없으면 재료명이 아니다 — '8g', '1' 같은 파싱 잔여물이 사전에 들어가는 걸 막는다.
    if not re.search(r"[가-힣]", name):
        return ""
    return re.sub(r"\s+", "", name).lower()[:64]


def sanitize_parts(raw: str) -> str:
    """재료 필드에만 섞여 있는 HTML(<br>, <strong>) 정리. <br> 는 줄바꿈 역할을 한다."""
    raw = re.sub(r"<\s*br\s*/?\s*>", "\n", raw, flags=re.I)
    return re.sub(r"</?\s*strong\s*>", "", raw, flags=re.I)


def parse_parts(raw: str) -> list[tuple[str | None, str]]:
    """RCP_PARTS_DTLS → [(group_name, raw_text), ...]"""
    raw = sanitize_parts(raw)
    lines, merged = [x.strip() for x in raw.split("\n") if x.strip()], []
    for line in lines:
        if merged and merged[-1].rstrip().endswith(","):
            merged[-1] = merged[-1].rstrip() + " " + line
        else:
            merged.append(line)

    group, out = None, []
    for line in merged:
        body = re.sub(rf"^[{MARKERS}]\s*", "", line).strip()
        # 대괄호는 두 가지로 쓰인다: '[1인분]' 은 인분 표기라 버리고,
        # '[가정 간편식 재료]' 는 그룹명이라 살린다.
        bracket = re.match(r"^\[([^\]]*)\]\s*(.*)$", body)
        if bracket:
            label, body = bracket.group(1).strip(), bracket.group(2).strip()
            if not re.fullmatch(r"\d+\s*인분", label):
                group = label[:64]
        if not body or body in {".", "-"} or re.fullmatch(r"\d+\s*인분\s*기준", body):
            continue
        m = re.match(r"^([^:]{1,30}?)\s*:\s*(.*)$", body)
        if m:
            group, body = m.group(1).strip()[:64], m.group(2).strip()
            if not body:
                continue
        else:
            head = body.split()
            if head and head[0] in GROUP_WORDS and len(head) > 1:
                group, body = head[0][:64], " ".join(head[1:])
            elif len(body) <= 20 and "," not in body and not re.search(r"\d", body):
                group = body[:64]
                continue
        for token in split_top_level(body):
            # '크림치즈 > 우유 875ml' 처럼 '>' 로 그룹을 여는 표기. 줄 중간에서도 새 그룹이 열린다.
            if ">" in token:
                head, _, tail = token.partition(">")
                head, token = head.strip(), tail.strip()
                if head and not re.search(r"\d", head):
                    group = head[:64]
                if not token:
                    continue
            out.append((group, token[:255]))
    return out


def parse_steps(row: dict) -> list[tuple[int, str, str | None]]:
    """(seq, content, image_url). 원본은 인덱스에 구멍이 있으므로 1..N 으로 다시 매긴다."""
    steps = []
    for i in range(1, 21):
        text = clean(row.get(f"MANUAL{i:02d}"))
        if not text:
            continue
        text = re.sub(r"^\d+[.)]?\s*", "", text)  # '1. ' 번호 접두
        text = re.sub(r"(?<=[^\sA-Za-z])[A-Za-z]$", "", text)  # 끝에 붙은 이미지 앵커 문자
        text = re.sub(r"\s+", " ", text).strip()
        if text:
            steps.append((text, clean(row.get(f"MANUAL_IMG{i:02d}")) or None))
    return [(n, c, img) for n, (c, img) in enumerate(steps, start=1)]


# ---------------------------------------------------------------- SQL emit


def q(v) -> str:
    if v is None or v == "":
        return "null"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def emit(f, header: str, columns: str, select: str, casts: list[str], vcols: str,
         rows: list[tuple], conflict: str) -> None:
    if not rows:
        return
    f.write(f"\n-- {header}: {len(rows)}행\n")
    for i in range(0, len(rows), CHUNK):
        block = rows[i : i + CHUNK]
        f.write(f"insert into {columns}\n{select}\nfrom (values\n")
        lines = []
        for j, r in enumerate(block):
            cells = [q(v) for v in r]
            if j == 0:  # 첫 행에만 타입을 못박아 null 컬럼의 타입 추론 실패를 막는다
                cells = [f"{c}::{t}" for c, t in zip(cells, casts)]
            lines.append("  (" + ", ".join(cells) + ")")
        f.write(",\n".join(lines))
        f.write(f"\n) as v({vcols})\n{conflict};\n")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", help="이미 받아둔 JSON 배열 파일로 생성")
    args = ap.parse_args()

    if args.cache:
        rows = json.loads(Path(args.cache).read_text(encoding="utf-8"))
        print(f"캐시에서 {len(rows)}행")
    else:
        print("식약처 API 수집 중...")
        rows = fetch_all()

    recipes, steps, ings, dic, nutri, tags, raws = [], [], [], {}, [], set(), []
    stat = {"tok": 0, "amt": 0, "grp": 0}

    for r in rows:
        sid = clean(r.get("RCP_SEQ"))
        if not sid:
            continue
        recipes.append((
            sid, clean(r.get("RCP_NM"))[:255], clean(r.get("RCP_PAT2"))[:32] or None,
            clean(r.get("RCP_WAY2"))[:32] or None,
            clean(r.get("ATT_FILE_NO_MK"))[:512] or None,
            clean(r.get("ATT_FILE_NO_MAIN"))[:512] or None,
        ))
        for seq, content, img in parse_steps(r):
            steps.append((sid, seq, content, (img or "")[:512] or None))

        seq = 0
        for group, raw_text in parse_parts(clean(r.get("RCP_PARTS_DTLS"))):
            name, amount, unit = parse_ingredient(raw_text)
            key = norm_key_of(name)
            stat["tok"] += 1
            stat["amt"] += 1 if amount is not None else 0
            stat["grp"] += 1 if group else 0
            if key:
                dic.setdefault(key, name)
            seq += 1
            ings.append((sid, seq, key or None, group, amount, (unit or "")[:16] or None, raw_text))

        nutri.append((
            sid, to_number(clean(r.get("INFO_WGT"))), to_number(clean(r.get("INFO_ENG"))),
            to_number(clean(r.get("INFO_CAR"))), to_number(clean(r.get("INFO_PRO"))),
            to_number(clean(r.get("INFO_FAT"))), to_number(clean(r.get("INFO_NA"))),
        ))
        for tag in split_top_level(clean(r.get("HASH_TAG"))):
            tag = tag.strip()[:32]
            if tag:
                tags.add((sid, tag))
        raws.append((sid, json.dumps(r, ensure_ascii=False)))

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    main_sql, raw_sql = OUT_DIR / "mfds_cookrcp01.sql", OUT_DIR / "mfds_cookrcp01_raw.sql"

    with main_sql.open("w", encoding="utf-8") as f:
        f.write(
            "-- 식약처 조리식품 레시피 DB(COOKRCP01) 시드 데이터.\n"
            "-- 생성: scripts/mfds_ingest.py   출처: 식품의약품안전처 (KOGL 출처표시)\n"
            "--\n"
            "-- Flyway 경로(db/migration) 밖이므로 자동 실행되지 않는다. 수동으로 넣는다:\n"
            "--   psql \"$DATABASE_URL\" -v ON_ERROR_STOP=1 -f mfds_cookrcp01.sql\n"
            "--\n"
            "-- 전체가 한 트랜잭션이고 모든 INSERT 가 on conflict do nothing 이라 재실행해도 안전하다.\n"
            "-- 자식 행은 id 를 박지 않고 (source, source_id) 로 조인해 찾는다 — identity 컬럼과 충돌하지 않는다.\n"
            "begin;\n"
        )
        emit(
            f, "recipes",
            "recipes (source, source_id, title, category, cook_method, image_url, image_thumb_url,\n"
            "                     servings, license, is_published, created_at, updated_at)",
            "select 'mfds', v.source_id, v.title, v.category, v.cook_method, v.image_url, v.image_thumb_url,\n"
            "       1, 'kogl', true, now(), now()",
            ["varchar", "varchar", "varchar", "varchar", "varchar", "varchar"],
            "source_id, title, category, cook_method, image_url, image_thumb_url",
            recipes, "on conflict on constraint uq_recipes_source do nothing",
        )
        emit(
            f, "ingredients (사전)", "ingredients (name, norm_key)",
            "select v.name, v.norm_key", ["varchar", "varchar"], "name, norm_key",
            [(n, k) for k, n in sorted(dic.items())],
            "on conflict on constraint uq_ingredients_norm_key do nothing",
        )
        emit(
            f, "recipe_steps", "recipe_steps (recipe_id, seq, content, image_url)",
            "select r.id, v.seq, v.content, v.image_url",
            ["varchar", "smallint", "text", "varchar"], "source_id, seq, content, image_url",
            steps,
            "join recipes r on r.source = 'mfds' and r.source_id = v.source_id\n"
            "on conflict on constraint uq_recipe_steps_recipe_seq do nothing",
        )
        emit(
            f, "recipe_ingredients",
            "recipe_ingredients (recipe_id, seq, ingredient_id, group_name, amount, unit, raw_text)",
            "select r.id, v.seq, i.id, v.group_name, v.amount, v.unit, v.raw_text",
            ["varchar", "smallint", "varchar", "varchar", "numeric(10,2)", "varchar", "varchar"],
            "source_id, seq, norm_key, group_name, amount, unit, raw_text",
            ings,
            "join recipes r on r.source = 'mfds' and r.source_id = v.source_id\n"
            "left join ingredients i on i.norm_key = v.norm_key\n"
            "on conflict on constraint uq_recipe_ingredients_recipe_seq do nothing",
        )
        emit(
            f, "recipe_nutrition",
            "recipe_nutrition (recipe_id, basis, serving_weight_g, calories, carbs_g, protein_g, fat_g, sodium_mg)",
            "select r.id, 'PER_SERVING', v.wgt, v.cal, v.car, v.pro, v.fat, v.na",
            ["varchar"] + ["numeric(8,2)"] * 6, "source_id, wgt, cal, car, pro, fat, na",
            nutri,
            "join recipes r on r.source = 'mfds' and r.source_id = v.source_id\n"
            "on conflict on constraint pk_recipe_nutrition do nothing",
        )
        emit(
            f, "recipe_tags", "recipe_tags (recipe_id, tag)", "select r.id, v.tag",
            ["varchar", "varchar"], "source_id, tag", sorted(tags),
            "join recipes r on r.source = 'mfds' and r.source_id = v.source_id\n"
            "on conflict on constraint uq_recipe_tags_recipe_tag do nothing",
        )
        f.write("\ncommit;\n")

    with raw_sql.open("w", encoding="utf-8") as f:
        f.write(
            "-- 식약처 COOKRCP01 원본 응답 스냅샷 → recipe_raw.\n"
            "-- 선택 사항이다. 재수집 없이 파서만 고쳐 다시 만들고 싶을 때를 위한 것으로,\n"
            "-- mfds_cookrcp01.sql 을 먼저 넣은 뒤에 실행한다.\n"
            "begin;\n"
        )
        emit(
            f, "recipe_raw", "recipe_raw (recipe_id, payload)", "select r.id, v.payload::jsonb",
            ["varchar", "text"], "source_id, payload", raws,
            "join recipes r on r.source = 'mfds' and r.source_id = v.source_id\n"
            "on conflict on constraint pk_recipe_raw do nothing",
        )
        f.write("\ncommit;\n")

    print(f"\n레시피 {len(recipes)} · 단계 {len(steps)} · 재료 {len(ings)} · 사전 {len(dic)} · "
          f"영양 {len(nutri)} · 태그 {len(tags)}")
    t = stat["tok"] or 1
    print(f"파싱 품질: 수량 추출 {stat['amt']}/{t} ({stat['amt'] * 100 // t}%) · "
          f"그룹 부여 {stat['grp']}/{t} ({stat['grp'] * 100 // t}%)")
    for p in (main_sql, raw_sql):
        print(f"  {p.relative_to(ROOT)}  {p.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
