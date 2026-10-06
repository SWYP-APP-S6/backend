# 배포

Naver Cloud Platform 단일 VM 위에 `docker compose` 로 전체 스택(app + PostgreSQL + Redis)을
띄운다. 앱은 루프백에만 바인딩되고, 외부 트래픽은 nginx 가 리버스 프록시로 넘긴다.

```
인터넷 ──443/80──> nginx (VM) ──127.0.0.1:8080──> app 컨테이너   (dev 스택은 아래 "dev 서버")
                                                    │
                                        도커 네트워크 ├─> postgres 컨테이너
                                                    └─> redis 컨테이너
```

## 배포하는 법

브랜치 흐름은 **feature → `dev` → `main`** 이다. 기능 PR 은 `dev` 로 merge 해 dev 서버에서 확인하고,
릴리스 때 `dev → main` PR 로 운영에 올린다.

```bash
ssh root@<서버 IP>
prod    # 운영: ~/backend     ← main
dev     # dev:  ~/backend-dev ← dev
```

둘 다 서버에 직접 둔 한 줄짜리 래퍼다(레포에는 없다). `/usr/local/bin/prod`:

```sh
#!/bin/sh
exec su - deploy -c /home/deploy/backend/scripts/deploy.sh
```

`/usr/local/bin/dev` 는 경로만 `/home/deploy/backend-dev/...` 로 다르다. 예전 이름 `deploy` 래퍼가
남아 있으면 `mv /usr/local/bin/deploy /usr/local/bin/prod` 로 바꾼다.

**배포는 항상 `deploy` 유저로 돌아야 한다** — root 로 `docker compose` 를 돌리면 컨테이너와
볼륨이 root 소유로 생겨 기존 것과 섞인다. 래퍼가 `su - deploy` 로 넘기므로 로그인 셸이 새로
뜨고 docker 그룹 권한도 정상적으로 잡힌다.

`deploy` 유저로 직접 들어와 있다면 스크립트를 그대로 실행해도 된다:

```bash
cd ~/backend && ./scripts/deploy.sh
```

[`scripts/deploy.sh`](scripts/deploy.sh) 가 `.env` 의 `DEPLOY_BRANCH`(기본 `main`)로 checkout·pull → `docker compose --profile app up -d --build`
→ `/ping` 헬스체크(최대 200초) → 이미지·빌드캐시 정리까지 한다. 헬스체크가 실패하면 앱 로그를
남기고 실패로 끝난다.

### 왜 GitHub Actions 로 배포하지 않는가

**이 저장소가 public 이기 때문**이다. self-hosted runner 를 붙이면 fork 의 pull request 가
이 서버에서 코드를 실행할 수 있고(GitHub 공식 문서도 self-hosted runner 는 private 저장소에만
쓰라고 권고한다), `deploy` 유저는 docker 그룹 소속이라 사실상 root 다.

private 으로 바꾸는 대신 public 을 유지하는 이유는 Free 플랜에서 잃는 것이 크기 때문이다 —
**CodeQL 코드 스캐닝은 public 저장소에서만 무료**이고(`.github/workflows/codeql.yml`),
**Actions 실행 분도 public 은 무제한**(private 은 월 2,000분)이다.

public 이라 서버가 레포를 인증 없이 clone 할 수 있으므로, 서버에 토큰이나 배포 키를 두지 않고도
pull 방향 배포가 성립한다. 서버로 들어오는 인바운드 연결도 없다.

컴파일·테스트(`.github/workflows/ci.yml` 의 `build` job)는 PR·push 마다 계속 자동으로 돈다.

## 서버 `.env` (필수 환경변수)

`~/backend/.env` — 저장소 안에 두지만 `.gitignore` 되어 있어 `git pull` 이 건드리지 않는다.
템플릿은 [`deploy.env.example`](deploy.env.example).

| 변수 | 필수 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | ✅ | `prod`. 이게 있어야 swagger 가 닫힌다 |
| `JWT_SECRET` | ✅ | 32바이트 이상. 없으면 앱이 기동하지 않는다 (`openssl rand -base64 48`) |
| `POSTGRES_PASSWORD` | ✅ | **볼륨 최초 초기화 때만 적용** — 첫 배포 때 정하고 이후 바꾸지 않는다 |
| `KAKAO_CONSUMER_APP_ID` | ✅ | 소비자 앱의 카카오 **숫자 앱 ID**(REST API 키 아님). 없으면 앱은 뜨지만 소비자 카카오 로그인이 전부 거부된다 |
| `KAKAO_OWNER_APP_ID` | ✅ | 점주 앱의 카카오 앱 ID. 소비자 앱과 **다른 카카오 앱**이다 |
| `KAKAO_LOCAL_REST_API_KEY` | ✅ | 점주 가게 등록(주소 → 좌표 변환)용 카카오 로컬 API 키. 위 로그인용 앱 ID들과 무관한 별개 값이다 — "카카오맵" 제품을 활성화한 앱의 **REST API 키**. 없으면 가게 등록이 전부 거부된다 |
| `FCM_PROJECT_ID` | ❌ | FCM 푸시용 Firebase 프로젝트 ID. 비면 앱은 뜨고 **푸시 발송만** 꺼진다(알림은 인앱 알림함까지 간다) |
| `FCM_CREDENTIALS_BASE64` | ❌ | 위 프로젝트의 서비스 계정 JSON 을 base64 한 줄로(`base64 -i key.json \| tr -d '\\n'`). 파일 마운트를 쓰지 않는 이유는 `compose.yaml` 주석 참고 |
| `FCM_ANDROID_CHANNEL_ID` | ❌ | 안드로이드 앱이 만드는 알림 채널 id. **앱과 값이 어긋나면 푸시가 도착해도 트레이에 안 뜬다** |
| `MFDS_API_KEY` | ❌ | 레시피 수집 배치 전용. 비어 있어도 앱은 뜬다 |
| `KAKAO_CONSUMER_REST_API_KEY` | ❌ | 관리자 웹 `/kakao-test` 의 인가 코드 교환 전용. 네이티브 앱 로그인은 SDK 토큰을 쓰므로 영향 없다 — 비면 그 페이지의 웹 로그인만 거부된다 |
| `KAKAO_OWNER_REST_API_KEY` | ❌ | 위와 같음(점주 앱 키) |
| `KAKAO_CONSUMER_CLIENT_SECRET` | ❌ | 위 REST API 키의 클라이언트 시크릿. 콘솔에서 켜 둔 앱이면 **필수** — 없으면 교환이 401(`invalid_client`/KOE010) |
| `KAKAO_OWNER_CLIENT_SECRET` | ❌ | 위와 같음(점주 앱 시크릿) |

값을 확인할 때는 시크릿이 터미널에 남지 않도록 키와 길이만 본다:

```bash
awk -F= '{print $1"="(length($2)?"<set, "length($2)" chars>":"<empty>")}' ~/backend/.env
```

## 운영 명령어 (서버에서 `deploy` 유저로)

```bash
cd ~/backend

docker compose --profile app ps             # 컨테이너 상태
docker compose --profile app logs -f app    # 앱 로그
docker compose --profile app restart app    # 앱만 재시작
docker stats --no-stream                    # 메모리 사용량
```

## 메모리 배분 (4GB VM 기준)

`compose.yaml` 에 `mem_limit` 으로 고정돼 있다. 상한이 없으면 한 서비스가 새어도 커널 OOM
Killer 가 **관계없는 컨테이너**를 죽인다.

| 서비스 | 상한 | 비고 |
|---|---|---|
| app | 1300m | `-XX:MaxRAMPercentage=75.0` 로 힙 ~975MB |
| postgres | 512m | `shared_buffers` 기본값(128MB) 대비 여유 |
| redis | 320m | `maxmemory 256mb` 위의 오버헤드 여유분 |

dev 스택을 함께 띄우면(아래 "dev 서버") 상시 사용량이 ~2.1GB → ~3.2GB 로 늘고, 빌드 순간의
여유는 swap 이 맡는다. 배포가 눈에 띄게 느려지거나 OOM 이 보이면 VM 을 8GB 로 올리는 것을 먼저
검토한다 — VM 을 하나 더 두는 것보다 싸고, 관리 대상(nginx·인증서·swap·`.env`)이 늘지 않는다.

나머지(~1.9GB)는 OS 와 **배포 중 Gradle 빌드**(순간 1.5~2GB) 몫이다. 이 순간의 안전망으로
서버에 swap 4GB + `vm.swappiness=10` 을 걸어둔다(OS 레벨이라 레포로 관리되지 않는 서버별 수동 설정).

> **왜 여전히 서버에서 빌드하는가**: `Dockerfile` 의 build 스테이지가 컨테이너 안에서
> `./gradlew bootJar` 를 돌린다. GitHub Actions 의 `build` job 은 **테스트 게이트일 뿐**
> 산출물을 서버로 보내지 않는다. 빌드를 서버에서 빼려면 이미지 레지스트리가 필요하고,
> 그건 아직 도입하지 않았다.

```bash
# swap 2GB → 4GB (서버에서 root 로, 1회)
swapoff /swapfile && rm /swapfile
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
free -h        # Swap 이 4Gi 인지 확인
```
`/etc/fstab` 에 `/swapfile none swap sw 0 0` 이 이미 있으면 재부팅 후에도 유지된다.

## dev 서버

운영과 **같은 VM** 에 compose 프로젝트를 하나 더 띄운다. 실사용자가 적어 dev 가 운영 자원을
잠깐 나눠 쓰는 위험이 VM 을 하나 더 두는 비용보다 작다고 판단했다. 파일(`compose.yaml`·
`deploy.sh`)은 운영과 같고, 다른 건 체크아웃 디렉터리와 `.env` 뿐이다.

```
api.mangro.cloud      ──> nginx ──127.0.0.1:8080──>  ~/backend      (프로젝트 backend)
dev-api.mangro.cloud  ──> nginx ──127.0.0.1:18080──> ~/backend-dev  (프로젝트 backend-dev)
```

compose 는 **프로젝트 이름(= 디렉터리 이름)** 으로 볼륨·네트워크·컨테이너를 가르므로 DB·Redis
데이터는 저절로 분리된다. 겹칠 수 있는 건 호스트 포트와 메모리뿐이라 그 둘만 `.env` 로 바꾼다.

| 서비스 | dev 상한 | 비고 |
|---|---|---|
| app | 768m | 힙 ~576MB(`MaxRAMPercentage=75`). 이보다 낮추면 메타스페이스·스레드 몫이 모자라다 |
| postgres | 256m | 데이터가 적어 `shared_buffers` 기본값(128MB)으로 충분 |
| redis | 128m | `maxmemory 64mb` |

### 처음 한 번 (서버)

1. **DNS**: `dev-api.mangro.cloud` A 레코드 → 운영과 같은 IP.
2. **체크아웃** (`deploy` 유저): `git clone https://github.com/SWYP-APP-S6/backend.git ~/backend-dev`.
   디렉터리 이름이 곧 프로젝트 이름이므로 **`backend-dev` 그대로** 둔다.
3. **`.env`**: `deploy.env.example` 을 복사하고 맨 아래 dev 블록의 주석을 푼다. 비밀값은 **운영과
   다른 값으로 새로 만든다** — `JWT_SECRET` 이 같으면 dev 토큰이 운영에서 통한다. 카카오 앱 ID 는
   같아도 된다. FCM 은 비워 두면 dev 에서 푸시만 꺼진다.
   `SPRING_PROFILES_ACTIVE` 는 비워 swagger 를 열어 둔다. **`COMPOSE_PROJECT_NAME` 은 운영·dev
   어느 쪽에도 넣지 않는다** — 운영에 넣으면 볼륨 이름(`backend_postgres-data`)이 바뀌어 빈 DB 로 뜬다.
4. **업로드 디렉터리**: `/srv/mangro-dev/uploads` 를 운영 업로드 디렉터리와 같은 소유자·권한으로 만든다.
5. **nginx**: 운영 `server` 블록을 복사해 `server_name` 을 `dev-api.mangro.cloud`, upstream 을
   `127.0.0.1:18080`, `/uploads` 경로를 `/srv/mangro-dev/uploads` 로 바꾸고 인증서를 발급한다.
6. **배포 래퍼** `/usr/local/bin/dev` (운영 `prod` 와 경로만 다르다 — 위 "배포하는 법"):
   ```sh
   #!/bin/sh
   exec su - deploy -c /home/deploy/backend-dev/scripts/deploy.sh
   ```
7. 첫 배포 후 관리자 계정은 dev DB 에 따로 만든다(`scripts/create_admins.sh`).

### 브랜치 고정

`deploy.sh` 는 체크아웃 상태와 무관하게 `.env` 의 `DEPLOY_BRANCH` 로 checkout 한 뒤 pull 한다 —
운영 `.env` 에는 이 값이 없어 `main`, dev `.env` 는 `dev`. 서버 체크아웃에서 브랜치를 손으로 바꿔
둬도 다음 배포가 되돌린다.

dev DB 에 먼저 적용된 마이그레이션이 그 뒤 수정되면 Flyway 체크섬 검증에서 기동이 멈춘다. 운영
DB 와는 무관하니 `docker compose --profile app down -v` 로 **dev 볼륨만** 지우고 다시 올리면 된다.
**`down -v` 는 반드시 `~/backend-dev` 에서 실행한다** — 운영 디렉터리에서 실행하면 운영 DB 가
사라진다.

## 참고

- [`compose.yaml`](compose.yaml) / [`Dockerfile`](Dockerfile) — 이미지·컨테이너 정의
- [`scripts/deploy.sh`](scripts/deploy.sh) — 배포 스크립트
- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — build 게이트
