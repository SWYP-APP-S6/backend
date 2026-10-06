---
description: Open a Pull Request for the work already committed this session. Never pushes without the branch, never merges.
---

Raise a Pull Request against `dev` for commits already made this session (via an explicit user
request — `/go` itself never commits, see CLAUDE.md rule 5). This is the explicit "ready for review"
trigger.

> PR is the review-gated integration path. Merge is a human decision (there is no auto-deploy).
> Feature work goes `feature → dev` (dev server), and a human releases with a `dev → main` PR
> (production). `/pr` opens only the feature PR; it does not merge and never pushes to `dev`/`main`.

Keep it thin: the build/tests were already verified before those commits were made — don't re-run them
here unless there's reason to doubt they're still current.

## Procedure

1. **Locate the commits**: `git fetch origin`, then `git log origin/dev..HEAD --oneline`.
   - Nothing ahead of `origin/dev` → stop (nothing to PR).
   - Uncommitted changes present → show them and ask whether to commit those too before opening the
     PR — commit only if the user says yes (rule 5); never commit unasked.

2. **Get the work onto a feature branch — never PR from `main` or `dev`.**
   - Already on another branch → use it.
   - On `main`/`dev` → move the commits off (`<base>` = the branch you were on):
     ```bash
     git switch -c <branch>
     git branch -f <base> origin/<base>
     ```
   - Branch name: `<type>/<slug>` from the lead commit subject.

3. **Rebase onto latest `origin/dev` if it moved**: `git rebase origin/dev` (skip if up to date).
   Stop and show the user on conflict — never resolve silently.

4. **Push the branch**: `git push -u origin <branch>`. If a prior `/pr` pushed it and step 3 rebased,
   re-push with `git push --force-with-lease origin <branch>` (lease, not blind `--force`).

5. **Open the PR** — first check for an existing one (`gh pr view <branch> --json url,state`):
   - OPEN PR exists → the push updated it; just report its URL. Don't create a second.
   - Else: `gh pr create --base dev --head <branch> --assignee @me`.
     - **Title**: the lead commit subject (or a short Korean noun phrase).
     - **Body** (Korean OK): `## 요약` / `## 변경` / `## 테스트`. Write to a file + `--body-file` when
       it has backticks/code.

6. **Report the PR URL.** Do not merge — a human merges when ready.

## Notes
- Pairs with `/go`: it produces reviewed, uncommitted code; once the user asks for a commit, `/pr`
  raises the PR for it.
- One PR per shippable unit.
- No auto-push / no auto-merge (CLAUDE.md rule 5).
