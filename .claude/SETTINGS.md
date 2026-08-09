# .claude/settings.json

Claude Code 권한 게이트 설정. AI 에이전트가 이 저장소에서 어떤 도구 호출을 권한 프롬프트 없이
자동 실행할 수 있는지 정의한다. 본 문서는 **공용 `settings.json`** 의 근거를 설명한다.

## 왜 공용에 적극적으로 추가하는가

권한 프롬프트가 잦으면 AI 에이전트의 실효 처리량이 크게 떨어진다. 매번 같은 read-only 명령에
yes/no를 누르는 비용이 누적되면 결국 사람이 직접 하는 것보다 느려진다. 공용에 안 올리면 팀원
각자가 `settings.local.json`에 같은 항목을 반복 추가하게 되고, 검증되지 않은 항목이 각자 환경에서
따로 자란다. 공용으로 끌어올리는 게 안전성·효용 양쪽에서 낫다.

원칙은 단순하다 — **정말 위험한 것만 빼고 적극적으로 공용에 올린다.** 코드 변경 자체는 PR
리뷰가 게이트하고, 의존성 변경은 `build.gradle.kts` diff가 게이트한다 — 권한 프롬프트가 이중으로
막을 필요는 없다.

## 정말 위험한 것 (공용에서 제외 → `settings.local.json`으로)

- **외부 시스템 인증·계정 의존**: 클라우드/클러스터 CLI(`aws`, `gcloud`, `kubectl`). 사람마다
  권한 범위가 다르고 잘못된 호출의 영향이 크다.
- **개인 디렉토리·환경 의존**: 개인 경로, 임시 env 토글.
- **사일런트 destructive**: `rm -rf`, `git push --force`를 와일드카드로 묶는 것. (좁은 형태는 OK)

## 항목

### 1. 빌드·검증 도구
| 패턴 | 근거 |
| --- | --- |
| `Bash(./gradlew:*)` / `Bash(gradlew:*)` | Gradle 래퍼 — 일상 도구(build/test/compileJava/bootRun/check). 출력이 즉시 보여 사일런트 부작용 경로가 없다. |

### 2. git / gh
| 패턴 | 근거 |
| --- | --- |
| `Bash(git:*)` | git 전체. mutation 포함이지만 일반 워크플로에 필수. **자동 push 금지는 권한이 아니라 CLAUDE.md 규칙 5로 강제**(모델이 명시 지시 없이 push하지 않는다). |
| `Bash(gh:*)` | GitHub CLI(PR/이슈 조회·생성) |

### 3. read-only 조회
`ls` / `find` / `grep` / `rg` / `cat` / `mkdir`(destructive 아님) 및 `curl http://localhost:*` /
`curl http://127.0.0.1:*`(로컬 `bootRun` 엔드포인트 확인 — 외부 도메인은 그대로 프롬프트).

### 4. PreToolUse hook — compound 명령 자동 승인

`hooks.PreToolUse`에 [hooks/allow-compound-readonly.py](hooks/allow-compound-readonly.py)가 등록돼 있다.

**왜.** Claude Code는 `&&`/`||`/`;`/`|`로 묶인 compound Bash 명령을 권한 매칭할 때, 각 세그먼트가
모두 allowlist에 있어도 프롬프트가 뜨는 알려진 버그가 있다. 탐색용 다세그먼트 명령
(`echo === && grep ... | head`)마다 프롬프트가 떠 처리량이 떨어진다.

**무엇을.** 명령을 세그먼트로 분해해 **모든** 세그먼트의 leading command가 (a) Claude Code 자체
read-only auto-approve 셋 또는 (b) 본 프로젝트 `PROJECT_ALLOWLIST`(`./gradlew`·`git`·`gh` 등)에
매칭되면 `allow`를 반환한다. 하나라도 빠지면 침묵 → 기존 프롬프트 흐름. **hook은 추가 승인만
부여하고 차단하지 않는다**(안전쪽 fail). `$(...)`/백틱/임시경로 밖 리다이렉션이 있으면 침묵한다.

**유지보수.** hook 안의 `PROJECT_ALLOWLIST`는 §1~3의 leading 토큰을 미러링한다. 새 패턴을
추가하면 hook에도 반영해야 compound 안에서 통과된다(안 해도 안전은 유지 — 프롬프트가 다시 뜰 뿐).

## 항목 추가 방법

권한 프롬프트가 자주 뜨는 패턴을 발견하면 우선 `settings.local.json`(개인, gitignore)에 며칠
써보고, "정말 위험한 것"이 아니면 공용 `settings.json`으로 이전하며 본 문서 표에 한 줄 추가한다.
