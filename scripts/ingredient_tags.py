"""식자재 태그 사전 → 시드 SQL 생성기.

식약처 레시피에서 뽑은 재료 사전(ingredients 1,709행)은 파싱 잔여물과 표기 흔들림이 섞여 있어
그대로 태그로 쓸 수 없다. 이 스크립트가 사람이 고른 대표 태그 목록(TAGS)과 별칭 규칙으로
사전 행을 대표 태그에 묶고, 그 결과를 멱등 SQL 로 낸다.

    python3 scripts/ingredient_tags.py

입력: src/main/resources/db/data/mfds_cookrcp01.sql 의 ingredients INSERT (mfds_ingest.py 산출물)
출력: src/main/resources/db/data/ingredient_tags.sql

- 대표 태그(is_tag = true)만 점주가 상품에 달 수 있고, category 가 화면의 분류 칩이 된다.
- 별칭 행은 canonical_id 로 대표 태그를 가리킨다. 레시피 매칭은 대표 태그 기준으로 모인다.
- EXCLUDED(물·소금·기름 등)는 태그도 별칭도 아니다 — 모든 레시피에 걸려 추천을 흐리기 때문이다.
- 어디에도 안 묶인 행은 태그로 고를 수 없을 뿐 레시피 화면에는 그대로 나온다.

목록을 고칠 때는 이 파일의 TAGS / EXCLUDED 를 고치고 다시 돌린다.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = ROOT / "src" / "main" / "resources" / "db" / "data"
SOURCE = DATA_DIR / "mfds_cookrcp01.sql"
OUTPUT = DATA_DIR / "ingredient_tags.sql"
CHUNK = 400

TAGS = {
    "채소": {
        "양파": "양파채썬것 양파채친것 적양파 레드어니언 깐양파 다진양파 양파다진것 간양파",
        "마늘": "통마늘 깐마늘 알마늘 편마늘 다진마늘 마늘다진것",
        "대파": "파채 대파채친것 대파흰부분 파 깐대파 다진대파 파다진것 다진파",
        "쪽파": "골파 실파송송썬것 실파",
        "당근": "당근채친것 당근채썬것 당근다진것",
        "고추": "홍고추 청고추 풋고추 붉은고추 청홍고추 다진청고추 다진홍고추 청고추다진것 홍고추다진것 송송썬붉은고추",
        "청양고추": "송송썬청양고추",
        "꽈리고추": "",
        "오이고추": "아삭이고추",
        "토마토": "홍토마토 ",
        "방울토마토": "체리토마토 줄기토마토 ",
        "감자": "알감자 ",
        "고구마": "호박고구마 자색고구마 밤고구마",
        "오이": "",
        "파프리카": "노랑파프리카 빨강파프리카 주황파프리카 붉은파프리카 노란파프리카 빨간파프리카 노란색파프리카 홍파프리카 황파프리카 적파프리카 청파프리카 미니파프리카 삼색파프리카 2가지색파프리카 2가지색미니파프리카",
        "피망": "피망채친것 청피망 홍피망 청피망다진것 홍피망다진것",
        "애호박": "주키니호박 쥬키니호박 돼지호박",
        "단호박": "미니단호박",
        "무": "무우 조선무 ",
        "배추": "통배추 단배추 알배추 배춧잎 배추잎 얼갈이배추",
        "양배추": "미니양배추 적양배추 적채",
        "양상추": "로메인상추 로메인",
        "상추": "청상추 ",
        "깻잎": "깻잎순 ",
        "시금치": "포항초 시금치포항초",
        "미나리": "",
        "가지": "",
        "브로콜리": "브러컬리 브로컬리",
        "콜리플라워": "컬리플라워",
        "부추": "호부추 조선부추 영양부추",
        "숙주": "",
        "콩나물": "",
        "청경채": "청경재 ",
        "쑥갓": "",
        "연근": "깐연근",
        "우엉": "",
        "비트": "",
        "아스파라거스": "",
        "셀러리": "샐러리잎 샐러리채친것 셀러리줄기 샐러리 샐러리다진것",
        "새싹채소": "어린싹 어린채소 어린잎 어린잎채소 새싹 베이비채소 무순",
        "참나물": "",
        "치커리": "",
        "케일": "",
        "콜라비": "",
        "달래": "",
        "두릅": "",
        "더덕": "",
        "도라지": "통도라지 ",
        "고사리": "",
        "취나물": "",
        "곤드레나물": "",
        "비름나물": "",
        "세발나물": "",
        "돌나물": "",
        "아욱": "",
        "근대": "",
        "쑥": "",
        "호박잎": "",
        "고구마순": "",
        "시래기": "시레기 무청 무청시래기 시래기불린것 ",
        "마늘종": "마늘쫑 ",
        "죽순": "",
        "토란": "",
        "래디시": "래디쉬 레디쉬",
        "생강": "통생강 깐생강 다진생강 생강다진것 생강즙",
        "옥수수": "옥수수알 옥수수콘 캔옥수수 영콘",
        "마": "산마 참마",
        "열무": "",
        "냉이": "",
        "봄동": "1인분봄동",
        "고수": "",
        "루콜라": "루꼴라",
        "인삼": "수삼",
    },
    "과일": {
        "사과": "사과즙 사과주스",
        "배": "배즙",
        "레몬": "레몬즙 레몬주스 레몬제스트 레몬껍질 레몬주수",
        "라임": "라임즙 라임주스",
        "바나나": "",
        "오렌지": "오렌지주스",
        "파인애플": "파인애플통조림",
        "블루베리": "",
        "크랜베리": "크렌베리 건크랜베리",
        "라즈베리": "",
        "키위": "참다래 ",
        "딸기": "",
        "아보카도": "",
        "감": "감말랭이 단감 홍시 곶감",
        "자두": "",
        "참외": "",
        "체리": "",
        "포도": "청포도 포도주스",
        "복숭아": "천도복숭아",
        "망고": "망고퓨레",
        "자몽": "",
        "멜론": "",
        "귤": "감귤 ",
        "석류": "",
        "수박": "",
        "대추": "대추채 대추슬라이스",
        "유자": "",
        "무화과": "건무화과",
        "복분자": "",
    },
    "버섯": {
        "표고버섯": "표고 건표고버섯 마른표고버섯 표고버섯마른것 생표고버섯",
        "새송이버섯": "미니새송이 미니새송이버섯 새송이벗서 새송이",
        "양송이버섯": "양송이",
        "느타리버섯": "참느타리버섯 느타리벗서 느타리 애느타리버섯",
        "팽이버섯": "팽이 황금팽이버섯",
        "목이버섯": "흰목이버섯 백목이버섯 건목이버섯",
        "만가닥버섯": "",
        "백일송이버섯": "백일송이",
    },
    "육류": {
        "소고기": "소등심 우삼겹 우목심 양지육 부챗살 쇠고기부채살 소고기갈빗살 소고기살치살 쇠고기구이용 소고기샤브샤브용 소채끝살 찜갈비 L.A갈비 쇠고기우둔 소고기치맛살 소고기정강이살 우육분쇄육 쇠고기 다진소고기 쇠고기다진것 다진쇠고기 쇠고기갈은것 소고기등심 쇠고기등심 소고기안심 소고기양지 쇠고기양지 소고기우둔살 쇠고기우둔살 소고기홍두깨살 소고기불고기용 소불고기 차돌박이 우민찌",
        "돼지고기": "돈등심 돈민찌 대패삼겹살 돼기고기 쪽갈비 돼지고기통삼겹살 돼지고기목심 돼지등갈비 돼지고기전지 한돈사태 돈육분쇄육 다진돼지고기 돼지고기다진것 돼지고기갈은것 돼지등심 돼지고기등심 돼지목살 돼지고기목살 돼지고기안심 돼지고기사태 돼지고기살코기 돼지고기삼겹살 삼겹살 통삼겹 돼지갈비 등갈비 돼지고기등갈비 돼지고기뼈갈비",
        "닭고기": "닭정육 닭 닭가슴살 닭고기가슴살 닭고기살 닭다리살 닭다릿살 닭안심살 닭봉",
        "오리고기": "훈제오리가슴살 훈제오리 오리고기훈제오리가슴살",
        "베이컨": "저염베이컨",
        "햄": "슬라이스햄 통조림햄",
        "소시지": "",
        "양고기": "양고기부채살",
    },
    "해산물": {
        "새우": "칵텔새우 흰다리새우 블랙타이거새우 백새우 칵테일새우 새우살 대하 새우대하 자숙새우 냉동새우살",
        "건새우": "보리새우",
        "오징어": "갑오징어 물오징어 통오징어 오징어몸통",
        "주꾸미": "쭈꾸미 ",
        "낙지": "낙지다리",
        "문어": "",
        "멸치": "국멸치 다시멸치 건멸치 잔멸치",
        "바지락": "바지락살",
        "홍합": "",
        "조개": "조갯살 백합 바지락모시조개 모시조개",
        "꼬막": "",
        "굴": "",
        "전복": "",
        "관자": "키조개관자 패주",
        "소라": "소라살",
        "골뱅이": "",
        "꽃게": "",
        "게살": "",
        "연어": "훈제연어 훈재연어 연어필레 연어훈제연어",
        "고등어": "",
        "삼치": "",
        "갈치": "",
        "조기": "",
        "가자미": "가자미살",
        "광어": "",
        "도미": "도미살",
        "대구": "대구살",
        "명태": "동태살 명태포 황태 황태채 황태포 황태머리 북어 북어채 코다리 동태포",
        "장어": "",
        "꽁치": "꽁치살",
        "민어": "",
        "과메기": "",
        "명란젓": "",
        "미더덕": "",
        "멍게": "멍게살",
        "다슬기": "다슬기살",
        "우렁이": "우렁 논우렁",
        "아귀": "",
        "날치알": "",
        "미역": "건조미역 미역줄기 마른미역줄기 건조자른미역 건미역 미역마른것 마른미역 건조미역줄기",
        "다시마": "건다시마 쌈다시마 다시마5×",
        "김": "생김 김가루 파래김",
        "매생이": "",
        "톳": "",
        "함초": "",
    },
    "달걀·유제품": {
        "달걀": "노른자 수란 달걀삶은것 계란 달걀흰자 달걀노른자 계란노른자 달걀물 삶은달걀 달걀지단",
        "메추리알": "",
        "우유": "저지방우유",
        "버터": "무염버터 저염버터",
        "생크림": "휘핑크림 무가당휘핑크림",
        "요거트": "요플레 플레인요거트 요구르트 플레인요구르트 호상요구르트 그릭요거트",
        "치즈": "피자치즈 체더치즈 까망베르치즈 마스카포네치즈 마스카르포네치즈 에벤탈치즈 스트링치즈 슬라이스체더치즈 간파르메산치즈 파마산치즈 모짜렐라치즈 모차렐라치즈 크림치즈 리코타치즈 슬라이스치즈 치즈슬라이스 치즈가루 저염치즈 어린이치즈 생모짜렐라치즈 파르메산치즈가루 그라나파다노치즈 슈레드모차렐라치즈",
    },
    "두부·콩": {
        "두부": "부침두부 포두부 검은콩두부 두부작은것 연두부큰것 연두부 순두부 쌈두부 으깬두부 두부면",
        "유부": "",
        "두유": "검은콩두유 무가당두유",
        "콩": "흰강낭콩 검정콩 노란콩 메주콩 대두 콩대두 콩백태 검은콩 서리태 강낭콩 병아리콩",
        "완두콩": "완두콩알 ",
        "팥": "",
        "낫토": "",
        "청국장": "청국장가루",
    },
    "곡류": {
        "쌀": "잡곡밥 오곡밥 찬밥 백미 멥쌀 불린쌀 밥 쌀밥",
        "현미": "찹쌀현미밥 현미불린것 현미쌀 볶은현미 현미밥",
        "찹쌀": "현미찹쌀",
        "흑미": "",
        "보리": "찰보리 보리쌀",
        "귀리": "오트밀 귀리밥",
        "퀴노아": "",
    },
    "면·떡·빵": {
        "떡": "절편 증편 쌀떡 떡볶이떡 가래떡 조랭이떡",
        "당면": "당면불린것",
        "국수": "칼국수면 생면 쫄면사리",
        "우동": "우동면",
        "빵": "치아바타 포카치아빵 버거빵 찰깨빵",
        "라면": "",
        "소면": "쌀소면 ",
        "파스타": "파스타면 먹물파스타 탈리아텔레 알파벳파스타 쌀파스타면 펜넬파스타 스파게티 스파게티면 펜네 푸실리 스파게티면건면",
        "메밀면": "",
        "쌀국수": "",
        "식빵": "",
        "바게트": "바케트 바케트빵 ",
        "또띠아": "토르티야 ",
        "라이스페이퍼": "라이스페퍼 ",
        "만두피": "",
        "춘권피": "",
        "누룽지": "",
        "카스텔라": "카스테라 ",
        "곤약": "곤약면 곤약국수 곤약미 곤약쌀 판곤약 실곤약",
        "묵": "메밀묵 올방개묵 우무묵 청포묵 도토리묵",
    },
    "김치·절임": {
        "김치": "깍두기 동치미 석박지 명이김치 김장김치 배추김치 묵은지 백김치 열무김치",
        "피클": "오이피클 할라피뇨피클",
        "쌈무": "",
    },
    "가공식품": {
        "게맛살": "크레미 크래비 게살크래미 맛살 크래미",
        "어묵": "찰어묵 ",
        "족발": "양념족발",
        "참치": "참치캔 참치살 캔참치",
    },
    "견과·건과": {
        "호두": "호두각 ",
        "잣": "",
        "땅콩": "땅콩분태 ",
        "아몬드": "통아몬드 아몬드플레이크 아몬드슬라이스",
        "캐슈넛": "",
        "피스타치오": "",
        "호박씨": "",
        "해바라기씨": "",
        "밤": "깐밤",
        "은행": "",
        "건포도": "",
    },
    "장·양념": {
        "간장": "저염간장 진간장 국간장 맛간장 어간장 간편어간장 저염국간장 저염진간장 멸치간장",
        "된장": "저염된장 미소된장 일본된장",
        "고추장": "",
        "쌈장": "",
        "고춧가루": "고운고춧가루 굵은고춧가루",
        "참기름": "",
        "들기름": "",
        "참깨": "통깨 깨 깨소금 부순참깨",
        "흑임자": "검은깨 검정깨",
        "들깨가루": "들깻가루 들깨",
        "꿀": "",
        "매실청": "매실액",
        "유자청": "",
        "생강청": "",
        "액젓": "멸치액젓 까나리액젓",
        "새우젓": "젓갈",
        "굴소스": "",
        "마요네즈": "",
        "케첩": "토마토케첩 케찹",
        "머스터드": "머스타드 홀그레인머스터드 디존머스타드 겨자 연겨자 겨자가루 겨잣가루",
        "토마토소스": "토마토페이스트 토마토페스트 홀토마토 토마토홀",
        "카레가루": "",
        "올리고당": "물엿 조청 요리당",
    },
}

EXCLUDED = {
    "물·육수": "물 얼음 쌀뜨물 육수 닭육수 다시마육수 다시마국물 다시마물 멸치육수 사골육수 해물육수 치킨육수 야채육수",
    "기본 조미료": "소금 소금적당량 소금약간 굵은소금 천일염 볶은소금 저염소금 함초소금 레몬소금 설탕 황설탕 흑설탕 설탕적당량 설탕약간 후춧가루 후추 통후추 흰후추 백후추 흰후춧가루 백후춧가루 후추적당량 후춧가루적당량 후추약간 흰후추약간 흰후춧가루적당량",
    "기름": "식용유 식용유적당량 올리브유 올리브오일 카놀라유 포도씨유 현미유 콩기름 대두유 식물성기름 튀김기름 기름 코코넛오일 마늘기름 마늘오일 고추기름",
    "식초·술·음료": "탄산수 사이다 식혜 홍초 식초 사과식초 양조식초 발사믹식초 감식초 레드와인식초 맛술 미림 미향 청주 정종 요리술 조미술 와인 화이트와인 레드와인 소주 막걸리 맥주",
    "가루·제과재료": "밀가루 박력분 강력분 중력분 박력밀가루 강력밀가루 우리밀가루 통밀가루 찹쌀가루 젖은찹쌀가루 쌀가루 멥쌀가루 메밀가루 부침가루 튀김가루 빵가루 전분 녹말가루 녹말 감자전분 옥수수전분 전분가루 물전분 녹말물 전분물 물녹말녹말가루 베이킹파우더 이스트 드라이이스트 생이스트 인스턴트이스트 인스턴트드라이이스트 젤라틴 판젤라틴 한천 슈가파우더 바닐라에센스 알룰로스 스테비아 타가토스",
    "허브·향신료": "건고추 마른고추 건홍고추 홍고추마른것 베트남건고추 바질 바질잎 바질가루 바질다진것 바질마른것 로즈마리 로즈메리 건로즈마리 파슬리 파슬리가루 파슬리다진것 월계수잎 오레가노 오레가노가루 오레가노다진것 오레가노마른것 타임 타임다진것 타임마른것 민트 애플민트 계피 계피가루 강황가루 정향 커민 차이브 치자 치자가루 파프리카가루 피클링스파이스",
    "소스·범용": "고추냉이 와사비 가쓰오부시 가다랑어포 코코넛밀크 코코넛우유 아몬드우유 연유 사워크림 딸기잼 땅콩버터 다크초콜릿 버섯마늘소금 토마토콩카세 발사믹소스 칠리소스 스리라차소스 두반장 노두유 바질페스토 크림소스 라면스프",
    "모호함": "혼합견과 혼합견과류 나무막대기 재료 과일 식용꽃 닭뼈 견과류 제철과일 흰살생선 생선살 해초 잡곡 미니 꼬치 호박",
}

MODIFIER_PREFIXES = [
    "송송썬", "2가지색", "무가당", "냉동", "통조림", "슬라이스", "삶은", "불린", "다진", "마른",
    "저염", "무염", "굵은", "고운", "으깬", "깐", "건", "생", "캔", "간",
]
TRAILING_NOISE = ["적당량", "약간", "소량", "다진것", "갈은것", "마른것", "채친것", "채썬것", "작은것", "큰것", "중간것", "불린것", "삶은것", "장식용", "중간크기"]
MEAT_PARTS = [
    "등심", "안심", "목살", "사태", "양지", "우둔살", "홍두깨살", "삼겹살", "갈비", "등갈비",
    "앞다리살", "뒷다리살", "가슴살", "다리살", "안심살", "날개", "살코기", "살", "몸통", "다리",
    "불고기용", "국거리", "다짐육",
]
COLOR_PREFIXES = ["빨강", "노랑", "주황", "빨간", "노란", "붉은", "초록", "적", "황", "홍", "청"]


def squeeze(name):
    return re.sub(r"\s+", "", name)


def strip_section(name):
    name = name.strip()
    if "(" not in name:
        name = name.rstrip(")")
        if ")" in name:
            name = name.split(")", 1)[1]
    name = re.sub(r"^[^:]*:\s*", "", name)
    name = re.sub(r"(?<=[가-힣])\s*[\d½⅓¼].*$", "", name)
    name = re.sub(r"\(.*?\)?$", "", name)
    return name.strip(" .,·")


def clean(name):
    key = squeeze(strip_section(name))
    changed = True
    while changed:
        changed = False
        for noise in TRAILING_NOISE:
            if key.endswith(noise) and len(key) > len(noise):
                key, changed = key[: -len(noise)], True
        for prefix in MODIFIER_PREFIXES:
            if key.startswith(prefix) and len(key) - len(prefix) >= 1:
                rest = key[len(prefix):]
                if rest in INDEX:
                    return rest
    return key


INDEX = {}
TAG_META = {}
for category, tags in TAGS.items():
    for tag, aliases in tags.items():
        TAG_META[tag] = category
        for key in [tag, *aliases.split()]:
            if key in INDEX and INDEX[key] != tag:
                sys.exit(f"duplicate key {key}: {INDEX[key]} vs {tag}")
            INDEX[key] = tag
EXCLUDE_INDEX = {}
for reason, names in EXCLUDED.items():
    for key in names.split():
        if key in INDEX:
            sys.exit(f"{key} is both a tag key and excluded")
        EXCLUDE_INDEX[key] = reason


def resolve(name):
    raw = squeeze(name)
    if raw in INDEX:
        return "tag", INDEX[raw], "명시"
    if raw in EXCLUDE_INDEX:
        return "excluded", EXCLUDE_INDEX[raw], "명시"
    key = clean(name)
    if key in INDEX:
        return "tag", INDEX[key], "규칙: 수식어·구분자 제거"
    if key in EXCLUDE_INDEX:
        return "excluded", EXCLUDE_INDEX[key], "규칙: 수식어·구분자 제거"
    for part in sorted(MEAT_PARTS, key=len, reverse=True):
        if key.endswith(part) and key[: -len(part)] in INDEX:
            base = INDEX[key[: -len(part)]]
            if TAG_META[base] in ("육류", "해산물"):
                return "tag", base, f"규칙: 부위({part})"
    for color in COLOR_PREFIXES:
        if key.startswith(color) and key[len(color):] in ("파프리카", "피망", "양파", "양배추"):
            return "tag", INDEX[key[len(color):]], f"규칙: 색상({color})"
    if key.endswith("가루") and key not in INDEX:
        return "excluded", "가루·제과재료", "규칙: ~가루"
    tokens = strip_section(name).split()
    if len(tokens) >= 2:
        kind, target, how = resolve(tokens[-1])
        if kind != "unmapped":
            return kind, target, "규칙: 앞 구분어 제거(마지막 낱말)"
    return "unmapped", None, ""


def norm_key_of(name):
    return squeeze(name).lower()[:64]


def read_dictionary():
    if not SOURCE.exists():
        sys.exit(f"error: {SOURCE} 이 없다. 'python3 scripts/mfds_ingest.py' 로 먼저 만든다.")
    text = SOURCE.read_text(encoding="utf-8")
    start = text.index("-- ingredients (사전)")
    section = text[start:text.index("\n-- ", start + 1)]
    pair = re.compile(r"^\s*\('((?:[^']|'')*)'(?:::varchar)?, '((?:[^']|'')*)'(?:::varchar)?\),?$", re.M)
    rows = [(m.group(1).replace("''", "'"), m.group(2).replace("''", "'")) for m in pair.finditer(section)]
    if not rows:
        sys.exit("error: ingredients 사전 행을 찾지 못했다 — mfds_cookrcp01.sql 형식이 바뀌었는지 확인한다.")
    return rows


def sql_str(value):
    return "'" + value.replace("'", "''") + "'"


def values_block(rows):
    lines = []
    for i, row in enumerate(rows):
        cells = [sql_str(v) + ("::varchar" if i == 0 else "") for v in row]
        lines.append("  (" + ", ".join(cells) + ")")
    return ",\n".join(lines)


def chunks(rows):
    for i in range(0, len(rows), CHUNK):
        yield rows[i:i + CHUNK]


def main():
    dictionary = read_dictionary()
    existing_keys = {key for _, key in dictionary}

    tags = []
    for category, names in TAGS.items():
        for tag in names:
            tags.append((tag, norm_key_of(tag), category))
    tag_keys = {key for _, key, _ in tags}

    aliases = []
    excluded = unmapped = 0
    for name, key in dictionary:
        kind, target, _ = resolve(name)
        if kind == "tag":
            if key != norm_key_of(target):
                aliases.append((key, norm_key_of(target)))
        elif kind == "excluded":
            excluded += 1
        else:
            unmapped += 1
    alias_keys = {alias for alias, _ in aliases}
    clash = alias_keys & tag_keys
    if clash:
        sys.exit(f"error: 태그이면서 별칭인 norm_key: {sorted(clash)}")

    out = [
        "-- 식자재 태그 사전. scripts/ingredient_tags.py 가 만든다 — 손으로 고치지 않는다.",
        "--",
        "-- mfds_cookrcp01.sql 이 넣은 ingredients 행을 **갱신**하므로 그 뒤에 넣는다. 사전에 없는 대표",
        f"-- 태그({len(tag_keys - existing_keys)}개)는 새 행으로 만든다. 한 트랜잭션이고 매번 표시를 지우고 다시",
        "-- 매기므로 재실행해도 안전하다 — 이 파일이 태그 목록의 단일 출처다.",
        "--",
        f"-- 대표 태그 {len(tags)}개 · 별칭 {len(aliases)}행 · 제외 {excluded}행 · 미분류 {unmapped}행 (사전 {len(dictionary)}행)",
        "begin;",
        "",
        "update ingredients set is_tag = false, canonical_id = null",
        "where is_tag or canonical_id is not null;",
        "",
        "-- 대표 태그",
        "insert into ingredients (name, norm_key, category, is_tag)",
        "select v.name, v.norm_key, v.category, true",
        "from (values",
        values_block(tags),
        ") as v(name, norm_key, category)",
        "on conflict on constraint uq_ingredients_norm_key",
        "do update set category = excluded.category, is_tag = true;",
    ]
    out.append("")
    out.append("-- 별칭 → 대표 태그")
    for part in chunks(aliases):
        out += [
            "update ingredients a",
            "set canonical_id = t.id",
            "from (values",
            values_block(part),
            ") as v(alias_key, tag_key)",
            "join ingredients t on t.norm_key = v.tag_key",
            "where a.norm_key = v.alias_key;",
        ]
    out += [
        "",
        "-- 이미 별칭 행을 태그로 단 상품은 대표 태그로 옮긴다",
        "insert into product_ingredients (product_id, ingredient_id)",
        "select pi.product_id, i.canonical_id",
        "from product_ingredients pi",
        "join ingredients i on i.id = pi.ingredient_id",
        "where i.canonical_id is not null",
        "on conflict do nothing;",
        "",
        "delete from product_ingredients pi",
        "using ingredients i",
        "where i.id = pi.ingredient_id and i.canonical_id is not null;",
        "",
        "commit;",
        "",
    ]
    OUTPUT.write_text("\n".join(out), encoding="utf-8")
    print(f"tags {len(tags)} (new rows {len(tag_keys - existing_keys)}), aliases {len(aliases)}, "
          f"excluded {excluded}, unmapped {unmapped} / {len(dictionary)} -> {OUTPUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
