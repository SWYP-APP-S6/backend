#!/usr/bin/env python3
"""
PreToolUse hook for the Bash tool.

Workaround for Claude Code bugs where compound Bash commands (joined by
`&&`, `||`, `;`, `|`, `|&`) prompt for permission even though every
individual segment matches the project's allowlist or Claude Code's own
read-only auto-approve set. See:
  - https://github.com/anthropics/claude-code/issues/34810
  - https://github.com/anthropics/claude-code/issues/14595
  - https://github.com/anthropics/claude-code/issues/28183

Design rules:
1. The hook never DENIES — it only grants extra approvals. Anything it
   doesn't approve falls through to Claude Code's normal flow.
2. It approves a command only if EVERY segment's leading command is one
   the user has already chosen to trust, where "trust" means either:
     (a) it is on Claude Code's own auto-approve read-only set
         (see READONLY_BUILTINS / READONLY_PAIRS), or
     (b) it appears in PROJECT_ALLOWLIST below, which mirrors the
         leading-command tokens this project's .claude/settings.json
         (and settings.local.json) already grants `Bash(<cmd>:*)` for.
3. If the command contains `$(...)` or backtick command substitution
   (which executes arbitrary substituted commands), the hook stays
   silent so the normal prompt flow runs.
4. Heredocs and `>` / `>>` file redirection are allowed only when every
   redirect target lives under a temp dir (`/tmp`, `$TMPDIR`, etc.).

PROJECT_ALLOWLIST must be kept in sync (manually) with
.claude/settings.json's `permissions.allow`.
"""

from __future__ import annotations

import json
import os
import re
import shlex
import sys
from urllib.parse import urlparse

SEGMENT_SEP = re.compile(r"\|\||&&|;|\|&|\|")

DANGER_PATTERNS = [
    re.compile(r"\$\("),
    re.compile(r"`"),
    # `<>` / `n<>` opens the target read-write; refuse to approve.
    re.compile(r"(?:^|\s|[0-9])<>"),
]

# `>`, `>>`, `>|`, `2>`, `&>`, `n>>`, and the no-space `cat foo>/x` form.
REDIRECT_RE = re.compile(r"(?<!<)>>?\|?\s*(\S+)")

_TMPDIR = os.environ.get("TMPDIR", "/tmp").rstrip("/")
TEMP_REAL_PREFIXES = tuple(
    sorted(
        {
            os.path.realpath(p).rstrip("/") + "/"
            for p in ("/tmp", "/var/tmp", _TMPDIR or "/tmp")
            if p
        }
    )
)

FD_DUP_RE = re.compile(r"^&(?:\d+|-)$")


def _is_temp_path(path: str) -> bool:
    p = path.strip().strip("'\"")
    if not p:
        return False
    if FD_DUP_RE.match(p):  # `2>&1`, `2>&-` — not a file target.
        return True
    # Shell parameter expansion means the literal path != what Bash opens.
    if "$" in p:
        return False
    try:
        resolved = os.path.realpath(p)
    except (OSError, ValueError):
        return False
    resolved = resolved.rstrip("/") + "/"
    return any(resolved.startswith(prefix) for prefix in TEMP_REAL_PREFIXES)


def redirects_are_temp_only(cmd: str) -> bool:
    targets = REDIRECT_RE.findall(cmd)
    if not targets:
        return True
    return all(_is_temp_path(t) for t in targets)


READONLY_BUILTINS = {
    "cd", "ls", "cat", "head", "tail", "wc", "echo", "printf", "grep",
    "egrep", "fgrep", "rg", "find", "fd", "fdfind", "sort", "uniq", "cut",
    "paste", "tr", "column", "tac", "rev", "fold", "expand", "unexpand",
    "fmt", "nl", "comm", "awk", "sed", "jq", "yq", "pwd", "whoami", "id",
    "date", "hostname", "uname", "which", "type", "test", "true", "false",
    "diff", "cmp", "stat", "file", "readlink", "basename", "dirname",
    "realpath", "env", "printenv", "sleep", "expr", "seq", "df", "du", "ps",
    "free", "xargs", "tree", "tput", "sha256sum", "sha1sum", "md5sum",
    "base64", "man", "info", "help",
}

READONLY_PAIRS = {
    ("git", "status"), ("git", "log"), ("git", "diff"), ("git", "show"),
    ("git", "blame"), ("git", "branch"), ("git", "tag"), ("git", "remote"),
    ("git", "ls-files"), ("git", "ls-remote"), ("git", "rev-parse"),
    ("git", "describe"), ("git", "reflog"), ("git", "shortlog"),
    ("git", "cat-file"), ("git", "for-each-ref"), ("git", "worktree"),
    ("git", "grep"),
    ("gh", "pr"), ("gh", "issue"), ("gh", "run"), ("gh", "workflow"),
    ("gh", "repo"), ("gh", "release"), ("gh", "auth"), ("gh", "api"),
    ("docker", "ps"), ("docker", "images"), ("docker", "logs"),
    ("docker", "inspect"),
}

# Leading-command tokens granted broad `Bash(<cmd>:*)` in .claude/settings.json.
PROJECT_ALLOWLIST = {
    "./gradlew",
    "gradlew",
    "git",
    "gh",
    "mkdir",
}

# Commands whose real action is a subcommand (first non-flag token).
MULTI_SUB_CMDS = {
    "git",
    "gh",
    "docker",
    "./gradlew",
    "gradlew",
}

ENV_PREFIX = re.compile(r"^(\w+=[^\s]+\s+)+")
WRAPPER_LEAD = re.compile(r"^(sudo|time|env|nice|nohup)\s+")
TIMEOUT_LEAD = re.compile(r"^timeout\s+\S+\s+")

LOCALHOST_HOSTS = {"localhost", "127.0.0.1", "::1", "[::1]"}


def has_danger_token(cmd: str) -> bool:
    return any(p.search(cmd) for p in DANGER_PATTERNS)


def split_segments(cmd: str) -> list[str]:
    return [seg.strip() for seg in SEGMENT_SEP.split(cmd) if seg.strip()]


def extract_leading(seg: str) -> tuple[str, str | None, list[str]] | None:
    s = ENV_PREFIX.sub("", seg.strip())
    while True:
        m = WRAPPER_LEAD.match(s)
        if m:
            s = s[m.end():]
            continue
        m = TIMEOUT_LEAD.match(s)
        if m:
            s = s[m.end():]
            continue
        break
    if not s:
        return None
    try:
        toks = shlex.split(s, posix=True)
    except ValueError:
        toks = s.split()
    if not toks:
        return None
    head = toks[0]
    sub: str | None = None
    if head in MULTI_SUB_CMDS:
        for t in toks[1:]:
            if not t.startswith("-"):
                sub = t
                break
    return head, sub, toks


CURL_OPTS_WITH_ARG = {
    "-d", "--data", "--data-raw", "--data-binary", "--data-urlencode",
    "-F", "--form", "-H", "--header", "-X", "--request", "-o", "--output",
    "-u", "--user", "-A", "--user-agent", "-e", "--referer", "-b",
    "--cookie", "-c", "--cookie-jar", "--url", "--connect-timeout",
    "--max-time", "--cacert", "--capath", "--cert", "--key", "--resolve",
    "-T", "--upload-file",
}


def is_curl_localhost(toks: list[str]) -> bool:
    i = 1
    while i < len(toks):
        t = toks[i]
        if t in CURL_OPTS_WITH_ARG:
            i += 2
            continue
        if t.startswith("-"):
            i += 1
            continue
        if "://" not in t:
            return False
        try:
            host = urlparse(t).hostname
        except ValueError:
            return False
        return host is not None and host.lower() in LOCALHOST_HOSTS
    return False


def is_trusted(head: str, sub: str | None, toks: list[str]) -> bool:
    if head in READONLY_BUILTINS:
        return True
    if sub is not None and (head, sub) in READONLY_PAIRS:
        return True
    if head in PROJECT_ALLOWLIST:
        return True
    if head == "curl" and is_curl_localhost(toks):
        return True
    return False


def should_approve(cmd: str) -> bool:
    if not cmd or not cmd.strip():
        return False
    if has_danger_token(cmd):
        return False
    if not redirects_are_temp_only(cmd):
        return False
    segments = split_segments(cmd)
    if not segments:
        return False
    for seg in segments:
        info = extract_leading(seg)
        if info is None:
            return False
        head, sub, toks = info
        if not is_trusted(head, sub, toks):
            return False
    return True


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0
    if payload.get("tool_name") != "Bash":
        return 0
    cmd = payload.get("tool_input", {}).get("command", "")
    if not should_approve(cmd):
        return 0
    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "allow",
                }
            }
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
