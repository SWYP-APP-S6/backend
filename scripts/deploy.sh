#!/usr/bin/env bash
# 서버에서 직접 실행하는 배포 스크립트.
#
#   ssh deploy@<서버>
#   cd ~/backend && ./scripts/deploy.sh
#
# 이 저장소는 public 이라 서버에 레포 접근용 토큰/키가 필요 없다. 반대로 public 저장소에
# self-hosted runner 를 붙이면 fork 의 pull request 가 이 호스트에서 코드를 실행할 수 있어
# 쓰지 않는다 — 그래서 배포는 서버가 끌어오는(pull) 방향으로만 일어난다.
set -euo pipefail

cd "$(dirname "$0")/.."

# `.env` 는 gitignore 되어 있어 git pull 이 건드리지 않는다. 없으면 앱이 기동하지 않으므로
# 컨테이너를 내리기 전에 먼저 막는다.
if [ ! -f .env ]; then
	echo "error: .env is missing — see deploy.env.example" >&2
	exit 1
fi

# 같은 VM 의 dev 체크아웃(~/backend-dev)도 이 스크립트를 쓴다. 스크립트가 보는 차이는 `.env` 의 두 값 —
# 운영은 main, dev 는 dev 브랜치를 올린다. 브랜치를 사람이 맞춰 두게 하지 않고 여기서 고정한다:
# 체크아웃이 다른 브랜치에 남아 있으면 그 브랜치가 그대로 운영에 나간다.
# `.env` 를 source 하지 않는 이유: base64·특수문자가 든 값이 셸에서 해석돼 깨질 수 있다.
env_value() {
	{ grep -E "^$1=" .env || true; } | tail -n 1 | cut -d= -f2-
}
APP_PORT=$(env_value APP_PORT)
APP_PORT=${APP_PORT:-8080}
DEPLOY_BRANCH=$(env_value DEPLOY_BRANCH)
DEPLOY_BRANCH=${DEPLOY_BRANCH:-main}

echo "==> deploying branch: $DEPLOY_BRANCH in $PWD (app port $APP_PORT)"

echo "==> pulling"
git fetch origin
git checkout "$DEPLOY_BRANCH"
git pull --ff-only origin "$DEPLOY_BRANCH"

echo "==> building and restarting"
docker compose --profile app up -d --build

echo "==> waiting for the app to answer"
for i in $(seq 1 40); do
	if curl -fsS "http://127.0.0.1:$APP_PORT/ping" > /dev/null; then
		echo "app is up"
		break
	fi
	if [ "$i" -eq 40 ]; then
		echo "error: app did not become healthy within 200s" >&2
		docker compose --profile app logs --tail=200 app
		exit 1
	fi
	sleep 5
done

# 배포마다 이전 이미지가 dangling 으로 남고 빌드 캐시도 쌓인다.
echo "==> pruning stale images and build cache"
docker image prune -f
docker builder prune -f --filter until=72h

echo "==> done"
