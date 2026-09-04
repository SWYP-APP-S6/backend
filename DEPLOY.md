# 배포

Naver Cloud Platform 단일 VM 위에 `docker compose` 로 전체 스택(app + PostgreSQL + Redis)을
띄운다. 앱은 루프백에만 바인딩되고, 외부 트래픽은 nginx 가 리버스 프록시로 넘긴다.

```
인터넷 ──443/80──> nginx (VM) ──127.0.0.1:8080──> app 컨테이너
                                                    │
                                        도커 네트워크 ├─> postgres 컨테이너
                                                    └─> redis 컨테이너
```

## 배포하는 법

```bash
ssh root@<서버 IP>
deploy
```

`/usr/local/bin/deploy` 는 서버에 직접 둔 한 줄짜리 래퍼다(레포에는 없다):

```sh
#!/bin/sh
exec su - deploy -c /home/deploy/backend/scripts/deploy.sh
```

**배포는 항상 `deploy` 유저로 돌아야 한다** — root 로 `docker compose` 를 돌리면 컨테이너와
볼륨이 root 소유로 생겨 기존 것과 섞인다. 래퍼가 `su - deploy` 로 넘기므로 로그인 셸이 새로
뜨고 docker 그룹 권한도 정상적으로 잡힌다.

`deploy` 유저로 직접 들어와 있다면 스크립트를 그대로 실행해도 된다:

```bash
cd ~/backend && ./scripts/deploy.sh
```

[`scripts/deploy.sh`](scripts/deploy.sh) 가 `git pull` → `docker compose --profile app up -d --build`
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
| `MFDS_API_KEY` | ❌ | 레시피 수집 배치 전용. 비어 있어도 앱은 뜬다 |

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

## 참고

- [`compose.yaml`](compose.yaml) / [`Dockerfile`](Dockerfile) — 이미지·컨테이너 정의
- [`scripts/deploy.sh`](scripts/deploy.sh) — 배포 스크립트
- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — build 게이트
