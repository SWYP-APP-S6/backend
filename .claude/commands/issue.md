---
description: Register improvements, tech debt, and future work as GitHub issues
---

Register work as GitHub issues (`SWYP-APP-S6/backend`). Input: free text ($ARGUMENTS), or — with no
argument — analyse the current conversation for open items.

## Procedure

1. **Collect candidates**: from `$ARGUMENTS`, or from the conversation — items a review/`/code-review`
   marked "out of scope", things the user deferred ("나중에", "다음에"), known tech debt.
2. **Split into work units**: one issue = one PR's worth of work.
3. **Draft each issue**:
   - **Title**: Korean noun phrase stating the change (English tech terms verbatim). No `[tag]` /
     `Domain:` prefixes. No issue-number cross-refs in the title (put `관련: #N` in the body).
   - **Body** (Korean OK): `## 배경` (왜 필요한지) / `## 현재 상태` / `## 개선 방향` / `## 관련 파일`.
   - **Label**: one of `enhancement` / `bug` / `refactor` / `tech-debt` / `test`.
   - **Parent** (optional): if it belongs under an umbrella/parent issue, note the number to attach as
     a native sub-issue at filing.
4. **Confirm**: show the list (each with its intended parent, if any) and confirm before posting. Run
   `gh issue list` first to avoid duplicates.
5. **File**: `gh issue create` (write the body to a file and use `--body-file` when it contains
   backticks/code — a heredoc body risks shell expansion). Attach sub-issues via the REST API when a
   parent was identified. Report the issue URLs.

## Notes
- Not urgent — no milestone/assignee.
- Title and body stay in Korean (internal team).
- A confirmed design decision that belongs to an issue being implemented goes into **that** issue's
  body (CLAUDE.md rule 3), not a new issue.
