---
name: fix-issue
description: Start work on a GitHub issue — verify a clean workspace, read the issue, branch off the correct base (the branch where the fix will ship) as issue-<nr>-<short-description>, then plan the implementation. Use when the user says "fix issue 123", "work on issue #45", or passes an issue number/URL.
allowed-tools: Bash, Read, Glob, Grep, WebFetch
---

# Start Work on a GitHub Issue

Prepares a clean, correctly-branched workspace for a GitHub issue, then either
implements a trivial fix directly or produces a plan when the solution is open.

The issue is given as an argument: a bare number (`123`), a `#`-prefixed number
(`#123`), or a full GitHub URL. Extract the number. If no argument was given, ask
the user which issue to work on before doing anything else.

This repository has several remotes; `origin` is `haumacher/phoneblock`. Always
target `origin` explicitly — never assume a single remote.

## Step 1 — Ensure the workspace is clean

Run `git status --porcelain`.

- **Clean (no output):** continue to step 2.
- **Not clean:** do **not** proceed. Run `git status` and `git diff --stat` (plus
  `git stash list` if relevant), describe the uncommitted/untracked changes to the
  user, and ask what to do with them (e.g. commit, stash, discard, or proceed on a
  branch from the current commit). Wait for the answer before continuing — never
  stash, commit, or discard on your own initiative.

## Step 2 — Read the issue

```bash
gh issue view <nr> --repo haumacher/phoneblock --comments
```

Read the title, body, and comments so you understand what is actually being asked.
If the issue is closed or cannot be found, stop and tell the user.

## Step 3 — Fetch the current state

```bash
git fetch origin --tags
```

This makes every up-to-date base candidate (all `origin/*` branches and tags)
available locally before the branch is created.

## Step 4 — Create and switch to the issue branch

**Create the branch before investigating any code.** Analysing the repository at
whatever commit happens to be checked out is meaningless — you must first be on
the base where the fix will actually ship, then study *that* code. So decide the
base branch now, from the issue alone; do not open source files to make this
choice.

**Pick the base branch = where the fix will ship.** Usually this is
`origin/master`. But some work ships on a maintenance or release line and must
branch from there instead — for example dongle firmware fixes destined for a
release branch off `dongle-X.Y.x` (or its rc tag), per CLAUDE.md's *Dongle
Release Branches* policy. Determine the target from the issue: which component it
touches and which release it is meant for. If the correct base is not obvious
(e.g. it could be `master` or a release line), **ask the user before branching** —
do not default to `origin/master` silently.

Derive a short, kebab-case description (~3–5 words, lowercase, ASCII) from the
issue title. Branch name schema:

```
issue-<nr>-<short-description>
```

Example: issue 142 "CardDAV sync fails for long contact names" →
`issue-142-carddav-long-contact-names`.

Create the branch from the chosen base and switch to it (substitute the base you
picked for `<base>`, e.g. `origin/master` or `dongle-1.4.x`):

```bash
git checkout -b issue-<nr>-<short-description> <base>
```

If a branch with that name already exists, mention it and ask the user whether to
reuse it, pick a different suffix, or replace it.

## Step 5 — Decide: implement directly or plan

Investigate the codebase as needed (`CLAUDE.md` describes the module layout) and
judge how clear-cut the fix is:

- **Trivial / unambiguous** — small change with one obvious correct solution
  (typo, clear bug with a single fix, a well-specified minor feature). Implement
  it directly, then verify (build/tests as appropriate) and report the result.
  No need to ask first.
- **Open or non-trivial** — the change is large, touches many areas, or there are
  several plausible solution alternatives with real trade-offs. Produce a concrete
  implementation plan (which files change, the approach chosen, alternatives
  considered, open questions), present it with `ExitPlanMode`, and wait for
  approval before editing files.

When in doubt between the two, prefer planning and asking.
