#!/usr/bin/env bash
# 로컬 db/data 시드를 운영 서버의 postgres 컨테이너로 밀어넣는다.
#
#   ./scripts/seed-remote.sh root@api.mangro.cloud
#   ./scripts/seed-remote.sh root@api.mangro.cloud --with-raw
#
# 파일은 SSH stdin 으로 흘려보낸다 — 서버에 사본이 남지 않으므로 뒷정리도 필요 없다.
# 시드는 전부 한 트랜잭션 + `on conflict do nothing` 이라 몇 번을 다시 돌려도 안전하다.
set -euo pipefail

usage() {
	cat >&2 <<'USAGE'
usage: scripts/seed-remote.sh <ssh-host> [--with-raw] [--reset]

  <ssh-host>   배포 서버. 예: root@api.mangro.cloud
  --with-raw   원본 API 응답(recipe_raw)까지 함께 넣는다. 파서만 고쳐 다시 만들 때 쓴다.
  --reset      기존 mfds 시드를 지우고 다시 넣는다. 시드는 on conflict do nothing 이라 이미
               있는 행을 갱신하지 않으므로, 내용이 바뀐 시드를 반영하려면 이 옵션이 필요하다.

환경변수:
  SEED_CONTAINER   postgres 컨테이너 이름 (기본: backend-postgres-1)
USAGE
	exit 1
}

HOST="${1:-}"
[ -n "$HOST" ] || usage
shift

WITH_RAW=0
RESET=0
for arg in "$@"; do
	case "$arg" in
		--with-raw) WITH_RAW=1 ;;
		--reset) RESET=1 ;;
		*) echo "error: 알 수 없는 옵션 '$arg'" >&2; usage ;;
	esac
done

CONTAINER="${SEED_CONTAINER:-backend-postgres-1}"
DATA_DIR="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/db/data"

# 연결을 한 번만 인증하고 재사용한다. 비밀번호 인증이면 파일마다 묻지 않게 된다.
# 유닉스 소켓 경로는 104자 제한이 있다 — macOS 의 긴 TMPDIR 을 쓰면 조용히 실패하므로 /tmp 에 둔다.
SOCKET="/tmp/seed-remote-$$"
SSH_OPTS=(-o ControlMaster=auto -o ControlPath="$SOCKET" -o ControlPersist=120)
cleanup() { ssh -O exit -o ControlPath="$SOCKET" "$HOST" 2>/dev/null || true; }
trap cleanup EXIT

# SQL 은 항상 stdin 으로 넘긴다. psql -c 로 넘기면 원격 셸이 따옴표를 한 번 더 벗겨
# `count(*)` 같은 인자에서 깨진다.
remote_psql() {
	ssh "${SSH_OPTS[@]}" "$HOST" "docker exec -i $CONTAINER psql -U swyp -d swyp -v ON_ERROR_STOP=1 $1"
}

apply() {
	local file="$1"
	if [ ! -f "$file" ]; then
		echo "error: $file 이 없다." >&2
		echo "       MFDS_API_KEY 를 .env 에 넣고 'python3 scripts/mfds_ingest.py' 로 먼저 만든다." >&2
		exit 1
	fi
	echo "==> $(basename "$file") ($(du -h "$file" | cut -f1))"
	remote_psql -q < "$file"
}

count() {
	remote_psql -tA <<< "select '$1=' || count(*) from $1"
}

if [ "$RESET" -eq 1 ]; then
	# 자식(recipe_steps/ingredients/nutrition/tags/raw)은 on delete cascade 로 함께 지워진다.
	# domain_events / recipe_feedback 은 cascade 가 아니므로 참조가 남아 있으면 여기서 멈춘다.
	#
	# identity 시퀀스는 delete 로 되돌아가지 않는다. 그대로 두면 재시드마다 recipes.id 가
	# 1157, 2313... 으로 밀리는데, 이 id 는 API 에 그대로 노출된다. 남은 행이 없으면 1 부터
	# 다시 시작하도록 맞춘다(다른 source 의 행이 남아 있으면 그 뒤부터).
	# ingredients 는 삭제 대상이 아니므로 건드리지 않는다.
	echo "==> 기존 mfds 시드 삭제 + id 시퀀스 초기화"
	remote_psql -q <<'SQL'
do $$
declare
	t text;
begin
	delete from recipes where source = 'mfds';
	foreach t in array array['recipes', 'recipe_steps', 'recipe_ingredients', 'recipe_tags'] loop
		execute format(
			'select setval(pg_get_serial_sequence(%L, ''id''), coalesce((select max(id) from %I), 0) + 1, false)',
			t, t);
	end loop;
end $$;
SQL
fi

apply "$DATA_DIR/mfds_cookrcp01.sql"

if [ "$WITH_RAW" -eq 1 ]; then
	apply "$DATA_DIR/mfds_cookrcp01_raw.sql"
fi

echo "==> 적용 결과"
count recipes
count recipe_steps
count ingredients
echo "==> done"
