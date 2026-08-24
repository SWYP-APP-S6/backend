---
description: Take a free-text task and run analysis → implementation → tests → review
---

Take a free-text task description ($ARGUMENTS) and run it end-to-end: analyse → implement → test →
review. **Commit and push are NOT part of this skill** — both are explicit user decisions (CLAUDE.md
rule 5). This skill stops with reviewed, working code on disk, uncommitted.

**Empty `$ARGUMENTS`** — ask the user what they want done.

Scale the ceremony to the work: trivial edits skip investigate/propose, and verification depth matches
the change's actual risk (rule 3) — a docs/config tweak doesn't need the same treatment as a behavior
change.

## Procedure

### 1. Entry
Parse background / change direction / likely starting files from the task description.

### 2. Investigate (non-trivial only)
Read the relevant files, or dispatch an Agent to map: affected classes/interfaces/tests, hidden
dependencies, layer-boundary implications (controller/service/repository). Cap at ~400 words with
`file:line` citations.

### 3. Propose + confirm (non-trivial only)
Summarise the approach as a short table/list; use `AskUserQuestion` to pick path/scope. Lead with the
correct-by-construction option, each with pros/cons. **Record confirmed decisions** — carry them into
the step 9 summary so they're available for the commit message whenever one gets made.

### 4. Implement: code + tests
- Respect CLAUDE.md architecture (package-by-feature, layer boundaries, DTOs at the controller edge,
  `@Transactional` at service).
- No comments in Java code — Javadoc included (rule 15).
- Write tests (JUnit 5). If coverage for the area is thin, write tests first (rule 7).
- When touching one file in several places, read it once and land all the changes together — don't
  re-read after each edit to "confirm" it landed; the tool call itself fails loudly if it didn't.
- Verify incrementally: `./gradlew test --tests "<FQCN or pattern>"` after each meaningful change.
- Keep prerequisite refactors separable (their own reviewable diff — rule 8); commit boundaries are
  decided later, by the user.

### 5. Static gate
Run the local gate once to avoid surprises later — skip if the change can't affect build/test outcome
(pure docs/config, rule 3):
```
./gradlew build
```
(compiles + runs tests + assembles.) Once Phase 2 guards exist, this also runs `spotlessCheck` /
`checkstyleMain` / `archTest` via `check`. Fix any failure — do not bypass. Keep pre-existing warnings
out of the diff (rule 10).

### 6. Behavior verification
- Step 5's `./gradlew build` already ran the full test suite — confirm it's green and watch the
  `skipped` count (a silent uptick is suspicious). No need to re-run the whole suite; a focused
  `./gradlew test --tests "<pattern>"` is enough if you changed tests since the build.
- **For an HTTP endpoint, verify the running behavior** (rule 16): a `@SpringBootTest`/`MockMvc`
  integration test, or `./gradlew bootRun` + `curl http://localhost:8080/...`. Compile passing ≠ done.
  Skip for changes that don't touch runtime behavior (rule 3).

### 7. Agent code review
- Review runs against the **uncommitted diff** — no commit needed first (`/code-review` Phase 1
  defaults to unstaged changes).
- Run the review per the **`/code-review` skill's Phase 2** (agent-count heuristic, background+parallel
  dispatch, the read-only-git agent constraint, the Spring review perspectives, and the
  Blocking/Should-fix/Nits/Looks good output). Never review your own code.

### 8. Apply review fixups
Address every Blocking and Should-fix directly with plain edits (or record why a finding is a false
positive) — there's no commit yet to amend. Re-run the regression once after.

### 9. Stop — summarize, don't commit
End the skill here with reviewed, working, uncommitted code. Do not run `git commit`, `git push`, or
open a PR — those happen only if the user explicitly asks (rule 5; use `/pr` for the PR).

## Output
Summarise each step briefly. End with: a 1–2 line change summary, files touched, one line per applied
review finding, and the confirmed design decisions ready to drop into a commit message. Close by
stating plainly that nothing has been committed and asking whether to commit now.
