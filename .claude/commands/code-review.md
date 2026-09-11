---
description: Code review against CLAUDE.md conventions (correctness + security + quality + boundaries + concurrency)
---

Review the change in scope $ARGUMENTS. With no argument, target unstaged changes; else staged; else the
most recent commit. A commit hash targets that commit's diff.

> **Shared contract.** `/go` (step 7) reuses **Phase 2** as the single source for how a review is run.
> Keep Phase 2 caller-agnostic.

## Phase 1: Identify scope
`git diff` (or the commit's diff) → collect the changed-file list and the full diff.

## Phase 2: Run the review

**Code review MUST go through a separate review agent. Never review code you authored yourself.**

### Choosing the agent count
**기본은 에이전트 1개다.** 에이전트를 하나 더 띄울 때마다 그 에이전트가 CLAUDE.md·`.claude/rules/*`·
변경 파일을 **처음부터 다시 읽는다** — 관점을 쪼갠 비용의 대부분은 리뷰가 아니라 이 중복 재독이다.

- **~600줄 이하 또는 feature 1개** → **1개**. 아래 관점 전부를 그 한 에이전트가 훑는다.
- 그 이상이면서 auth·DB 스키마·결제·보안 경계를 건드림 → **최대 2개**(1 = 정합성·동시성,
  2 = 경계·보안·계약). 3개 이상은 사용자가 명시적으로 요청할 때만.
- 관점을 쪼갤 때는 **파일도 쪼갠다** — 두 에이전트가 같은 파일을 읽게 두지 않는다.

### Dispatching
- **Background + parallel** (`run_in_background: true`).
- 각 에이전트에 주는 것: 전체 diff, 관점 체크리스트, 변경 의도와 함정. **호출자가 이미 확인한 사실을
  프롬프트에 적어 보내** 에이전트가 같은 파일을 다시 열지 않게 한다.
- **읽기 예산을 프롬프트에 박는다**: CLAUDE.md + 해당하는 `.claude/rules/*` + **변경 파일**,
  그리고 판정에 실제로 필요한 인접 파일만. 탐색적 전체 스캔·`find`로 트리 훑기 금지.
- **에이전트는 빌드를 돌리지 않는다** — `./gradlew build`는 호출자가 **한 번만** 돌린다.
- 호출자는 에이전트가 맡은 파일을 **중복해서 읽지 않는다**. 대신 결과가 오면 근거만 표적 검증한다.
- **Read-only-git constraint — bake into every agent's prompt.** The agent may run ONLY read-only git
  (`git --no-pager diff`/`show`/`log`/`status`) and is forbidden from any state-mutating command
  (`reset`/`checkout`/`restore`/`clean`/`stash`/`rebase`/`commit`/`add`) and any file edit. If it
  thinks state must change, it reports that instead of doing it.

### Review perspectives (cover all; bundle per the heuristic above)

**1. Security, auth & input validation**
- Endpoints/methods protected via Spring Security (`SecurityFilterChain`, `@PreAuthorize`) — not left
  open by default. Watch for IDOR (operating on an id from the request without an ownership/role check).
- Request bodies validated with Bean Validation (`@Valid` + constraints), not trusted raw.
- No SQL injection: JPA query methods / parameterized `@Query` — never string-built JPQL/SQL.
- No secrets hardcoded or in `application.properties` committed to git; externalize via env/config.
- Sequential writes across 2+ aggregates wrapped in a service `@Transactional`.

**2. Boundaries & architecture (CLAUDE.md)**
- `controller → service → repository` respected; controller doesn't touch repository/JPA directly.
- `@Entity` doesn't cross the controller boundary — DTOs in/out, mapping in service.
- Cross-feature access via the other feature's `service`, not its `repository`.
- `@Transactional` at the service layer; no external I/O (HTTP calls) inside a transaction.

**3. Code quality**
- Type safety (no raw types; `Optional` used correctly, not `.get()` blindly). Naming clarity.
- Magic numbers → constants. Duplicated / copy-pasted logic → shared helper. Single responsibility
  (no God service). No comments in Java code (CLAUDE.md rule 15).

**4. Efficiency**
- **N+1 queries**: JPA lazy associations fetched in a loop → use `@EntityGraph` / `join fetch` /
  projection. Load a slice, not the whole entity, when a projection suffices.
- Redundant repository calls; independent async work that could run concurrently.
- Transaction scope not wider than needed; no blocking I/O on hot paths.

**5. Correctness & tests**
- Edge cases covered by tests; behavior verified (not just compiles) for endpoints.
- Error handling maps to sensible HTTP responses (`@ExceptionHandler` / `ResponseStatusException`).

**6. Concurrency & idempotency**
- Read-check-then-write races: an existence/uniqueness check followed by a separate write isn't
  atomic — rely on a DB constraint (`UNIQUE`) or `@Version` (optimistic locking), not just an
  application-level `if`.
- Counter-style mutations (load entity → increment field → save, e.g. `viewCount`/`likeCount`) can
  lose updates under concurrent requests — prefer an atomic `UPDATE ... SET x = x + 1`, or flag the
  race explicitly if the current traffic doesn't warrant fixing it yet.
- Retried/duplicate requests (client retry, double-submit) produce the same result, not duplicate rows
  or double side-effects — relevant wherever a single-use token/resource is consumed (see
  `RefreshTokenService`'s rotation for the existing pattern).

### Output format
Group as **Blocking / Should-fix / Nits / Looks good**, each item with a `file:line` citation,
concise (≤700 chars per agent):
- **Blocking** — must not land (correctness / security / auth / data-loss).
- **Should-fix** — fix unless a recorded false positive.
- **Nits** — skip by default.

## Phase 3: Synthesise + apply
Apply the findings directly; skip false positives. Bucket by priority. If none, "Review passed".
User-facing strings / error messages in Korean where appropriate (CLAUDE.md language policy).
