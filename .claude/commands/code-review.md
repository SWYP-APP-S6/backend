---
description: Code review against CLAUDE.md conventions (correctness + security + quality + boundaries)
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
Decide dynamically by size / importance / domain risk. Small change in one area → 1 agent covers all
perspectives. Large + high-risk (auth, DB schema, security boundary) → split per perspective.

### Dispatching
- **Background + parallel** (`run_in_background: true`).
- Each agent gets: the full diff, its perspective checklist, and the change's intent + traps. It must
  read CLAUDE.md, Read each changed file, then review.
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
  (no God service). Pointless comments (keep non-obvious WHY only).

**4. Efficiency**
- **N+1 queries**: JPA lazy associations fetched in a loop → use `@EntityGraph` / `join fetch` /
  projection. Load a slice, not the whole entity, when a projection suffices.
- Redundant repository calls; independent async work that could run concurrently.
- Transaction scope not wider than needed; no blocking I/O on hot paths.

**5. Correctness & tests**
- Edge cases covered by tests; behavior verified (not just compiles) for endpoints.
- Error handling maps to sensible HTTP responses (`@ExceptionHandler` / `ResponseStatusException`).

### Output format
Group as **Blocking / Should-fix / Nits / Looks good**, each item with a `file:line` citation,
concise (≤700 chars per agent):
- **Blocking** — must not land (correctness / security / auth / data-loss).
- **Should-fix** — fix unless a recorded false positive.
- **Nits** — skip by default.

## Phase 3: Synthesise + apply
Apply the findings directly; skip false positives. Bucket by priority. If none, "Review passed".
User-facing strings / error messages in Korean where appropriate (CLAUDE.md language policy).
