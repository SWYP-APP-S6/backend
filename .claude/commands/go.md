---
description: Take a GitHub issue number OR a free-text task and run analysis → implementation → tests → review → commit
---

Take a GitHub issue number **or** a free-text task description ($ARGUMENTS) and run it end-to-end:
fetch/parse → analyse → implement → test → review → commit. **Push is NOT part of this skill** — that
is an explicit user decision (CLAUDE.md rule 5).

## Mode detection

- **Issue mode** — `$ARGUMENTS` is a single issue reference (`<N>` / `#<N>`, digits only). Final commit
  carries `Closes #N`.
- **Description mode** — free text (e.g. "add a health-check endpoint"). No issue; confirmed decisions
  go in the commit body; no `Closes` trailer. Use for small, contained work.
- **Empty** — ask the user what they want done.

Scale the ceremony to the work: trivial edits skip investigate/propose; large work (≥3 steps, many
files, or an architecture/convention change) should be an issue first (rule 2).

## Procedure

### 1. Entry
- **Issue mode**: `gh issue view <N> --json number,title,state,body,labels,comments`. If CLOSED, stop
  and ask. Read any `## 진행 상황` progress comment (resume point). **Freshness check**: verify the
  body's `file:line` citations / examples still hold before investing; if stale, propose a corrected
  body and confirm before `gh issue edit`.
- **Description mode**: parse background / change direction / likely starting files. If it grows
  issue-worthy mid-flight, pause and offer to file an issue.

### 2. Investigate (non-trivial only)
Read the relevant files, or dispatch an Agent to map: affected classes/interfaces/tests, hidden
dependencies, layer-boundary implications (controller/service/repository). Cap at ~400 words with
`file:line` citations.

### 3. Propose + confirm (non-trivial only)
Summarise the approach as a short table/list; use `AskUserQuestion` to pick path/scope. Lead with the
correct-by-construction option, each with pros/cons (rule 16). **Record confirmed decisions**: issue
mode → sync into the issue body (rule 3); description mode → into the commit body (step 9).

### 4. Implement: code + tests
- Respect CLAUDE.md architecture (package-by-feature, layer boundaries, DTOs at the controller edge,
  `@Transactional` at service).
- New/modified comments are **English, non-obvious WHY only** (rule 15).
- Write tests (JUnit 5). If coverage for the area is thin, write tests first (rule 7).
- Verify incrementally: `./gradlew test --tests "<FQCN or pattern>"` after each meaningful change.
- Keep prerequisite refactors in separate commits (rule 8).

### 5. Static gate
Run the local gate once to avoid surprises later:
```
./gradlew build
```
(compiles + runs tests + assembles.) Once Phase 2 guards exist, this also runs `spotlessCheck` /
`checkstyleMain` / `archTest` via `check`. Fix any failure — do not bypass. Keep pre-existing warnings
out of the feature commit (rule 10).

### 6. Behavior verification
- Step 5's `./gradlew build` already ran the full test suite — confirm it's green and watch the
  `skipped` count (a silent uptick is suspicious). No need to re-run the whole suite; a focused
  `./gradlew test --tests "<pattern>"` is enough if you changed tests since the build.
- **For an HTTP endpoint, verify the running behavior** (rule 16): a `@SpringBootTest`/`MockMvc`
  integration test, or `./gradlew bootRun` + `curl http://localhost:8080/...`. Compile passing ≠ done.

### 7. Agent code review
- **Commit the work BEFORE dispatching review** — review runs against committed state
  (`git diff <base>..HEAD`), so the work is unloseable even if a review agent misbehaves.
- Run the review per the **`/code-review` skill's Phase 2** (agent-count heuristic, background+parallel
  dispatch, the read-only-git agent constraint, the Spring review perspectives, and the
  Blocking/Should-fix/Nits/Looks good output). Never review your own code.

### 8. Apply review fixups
Address every Blocking and Should-fix (or record why a finding is a false positive). Single commit →
`git commit --amend --no-edit`; multi-commit → `git commit --fixup=<sha>` +
`GIT_SEQUENCE_EDITOR=true git rebase -i --autosquash <base>`. Re-run the regression once after.

### 9. Final commit
Assess completeness (issue mode): fully done → `Closes #N`; a coherent slice with scoped work
remaining → `Refs #N` + post a `## 진행 상황`(한 일 / 남은 일 / 다음 단계) issue comment as the resume
point.

```
git commit -m "$(cat <<'EOF'
<type>(<scope>): <subject>

<body — why + (description mode) the confirmed design decisions, wrapped ~72>

<Closes #N — issue mode, fully done; Refs #N if work remains; omit in description mode>
EOF
)"
```
- English message, conventional type. **No AI/Claude trailers** (CLAUDE.md commit convention — solo
  authored). Split commits by concern.

### 10. Push stays separate
`/go` stops at commit. Pushing / opening a PR is an explicit user decision — use `/pr` (rule 5).

## Output
Summarise each step briefly. End with: the commit SHA(s), a 1–2 line change summary, files touched,
one line per applied review finding, and (if partially done) 한 일 / 남은 일 / 다음 단계 with a note
that the issue stays open (`Refs`). Reminder: push is separate.
