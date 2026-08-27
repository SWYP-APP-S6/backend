#!/usr/bin/env bash
# 관리자 계정 INSERT 문을 만든다. 계정 생성 자체가 관리자 권한을 요구하므로 최초 계정들은
# 이렇게 직접 넣는다.
#
#   ./scripts/create_admins.sh < members.tsv > admins.sql
#   ./scripts/create_admins.sh < members.tsv | psql "$DATABASE_URL" -v ON_ERROR_STOP=1
#
# 입력(stdin)은 탭 구분 4열이다. `#` 로 시작하는 줄과 빈 줄은 건너뛴다:
#
#   email<TAB>name<TAB>SUPER|MANAGER|DEVELOPER<TAB>phone
#
# **명단을 이 스크립트에 넣지 않는다** — 이 저장소는 public 이라 커밋하면 팀원의 이메일과
# 전화번호가 그대로 공개된다. 입력 파일은 저장소 밖에 두거나 gitignore 한다.
#
# 초기 비밀번호는 **전화번호에서 숫자만 남긴 값**이다(01012345678). 팀원끼리 이미 아는 값이므로
# 서로의 계정에 로그인할 수 있다 — 각자 첫 로그인 후 `PUT /admin/auth/password` 로 반드시
# 바꿔야 한다. 이 스크립트가 만드는 것은 어디까지나 부트스트랩용 초기값이다.
set -euo pipefail

if ! command -v htpasswd > /dev/null; then
	echo "error: htpasswd 가 필요하다 (apache2-utils / httpd)" >&2
	exit 1
fi

# 재실행하면 기본적으로 기존 계정을 건드리지 않는다. 이미 비밀번호를 바꾼 사람을 다시
# 전화번호로 되돌리면 본인만 모르는 채 계정이 열린다.
CONFLICT_ACTION="do nothing"
if [ "${1:-}" = "--reset-passwords" ]; then
	# 비밀번호를 잊은 사람을 위해 전화번호로 되돌린다.
	CONFLICT_ACTION="do update set password = excluded.password, name = excluded.name,
                    type = excluded.type, updated_at = now()"
fi

sql_quote() {
	printf "%s" "$1" | sed "s/'/''/g"
}

printf "%-34s %-8s %-10s %s\n" "EMAIL" "NAME" "TYPE" "INITIAL PASSWORD" >&2
printf -- "--------------------------------------------------------------------\n" >&2

echo "begin;"

while IFS=$'\t' read -r email name type phone || [ -n "${email:-}" ]; do
	case "$email" in
		''|'#'*) continue ;;
	esac
	if [ -z "$name" ] || [ -z "$type" ] || [ -z "$phone" ]; then
		echo "error: 열이 부족하다 (email<TAB>name<TAB>type<TAB>phone): $email" >&2
		exit 1
	fi
	case "$type" in
		SUPER|MANAGER|DEVELOPER) ;;
		*) echo "error: 알 수 없는 type '$type' ($email)" >&2; exit 1 ;;
	esac

	# 하이픈/공백 표기가 사람마다 달라서 숫자만 남긴다 — 그래야 본인이 뭘 칠지 헷갈리지 않는다.
	password="$(printf "%s" "$phone" | tr -cd '0-9')"
	if [ "${#password}" -lt 8 ]; then
		echo "error: 전화번호에서 얻은 숫자가 8자 미만이라 비밀번호 정책에 걸린다: $email" >&2
		exit 1
	fi
	hash="$(htpasswd -bnBC 10 "" "$password" | tr -d ':\n')"

	printf "%-34s %-8s %-10s %s\n" "$email" "$name" "$type" "$password" >&2

	cat <<-EOF
	insert into admins (email, name, type, password, created_at, updated_at)
	values ('$(sql_quote "$email")', '$(sql_quote "$name")', '$type', '$hash', now(), now())
	on conflict (email) where deleted_at is null $CONFLICT_ACTION;
	EOF
done

echo "commit;"

printf -- "--------------------------------------------------------------------\n" >&2
echo "초기 비밀번호는 각자의 전화번호(숫자만)다. 첫 로그인 후 반드시 변경할 것." >&2
