---
description: Open a Pull Request for the work already committed this session. Never pushes without the branch, never merges.
---

Raise a Pull Request against `main` for the commits produced this session (by `/go` or by hand). This
is the explicit "ready for review" trigger.

> PR is the review-gated integration path. Merge is a human decision (there is no auto-deploy yet).
> `/pr` opens the PR; it does not merge and does not push to `main` directly.

Keep it thin: `/go` already ran the local gate at commit time — don't re-run build/test here.

## Procedure

1. **Locate the commits**: `git fetch origin`, then `git log origin/main..HEAD --oneline`.
   - Nothing ahead of `origin/main` → stop (nothing to PR).
   - Uncommitted changes present → show them and stop (commit via `/go` first).

2. **Get the work onto a feature branch — never PR from `main`.**
   - Already on a non-`main` branch → use it.
   - On `main` → move the commits off:
     ```bash
     git switch -c <branch>
     git branch -f main origin/main
     ```
   - Branch name: `issue-<N>-<slug>` when the commits carry `Closes #N`, else `<type>/<slug>` from the
     lead commit subject.

3. **Rebase onto latest `origin/main` if it moved**: `git rebase origin/main` (skip if up to date).
   Stop and show the user on conflict — never resolve silently.

4. **Push the branch**: `git push -u origin <branch>`. If a prior `/pr` pushed it and step 3 rebased,
   re-push with `git push --force-with-lease origin <branch>` (lease, not blind `--force`).

5. **Open the PR** — first check for an existing one (`gh pr view <branch> --json url,state`):
   - OPEN PR exists → the push updated it; just report its URL. Don't create a second.
   - Else: `gh pr create --base main --head <branch> --assignee @me`.
     - **Title**: the lead commit subject (or a short Korean noun phrase).
     - **Body** (Korean OK): `## 요약` / `## 변경` / `## 테스트`. Add `Closes #N` when the commits carry
       it. Write to a file + `--body-file` when it has backticks/code.

6. **Report the PR URL.** Do not merge — a human merges when ready.

## Notes
- Pairs with `/go`: it produces commits; `/pr` raises the PR.
- One PR per shippable unit (mirrors one-issue-one-PR).
- No auto-push / no auto-merge (CLAUDE.md rule 5).
