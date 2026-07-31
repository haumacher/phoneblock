---
name: cleanup-workspace
description: Park a finished worktree — commit real work, delete temporary/generated leftovers, then switch to the throwaway branch named after the workspace directory and reset it to origin/master. Use when the user says "clean up this workspace", "park this worktree", "reset back to master", or is done with a feature branch and wants the directory ready for the next task.
allowed-tools: Bash, Read, Glob, Grep
---

# Clean Up / Park a Workspace

This repository is used through several sibling git worktrees
(`/home/haui/devel/phoneblock`, `pb-1`, `pb-2`, `pb-3`, `pb-fix-sip`, …). Each
`pb-N` worktree has a same-named throwaway branch (`pb-1`, `pb-2`, …) that tracks
`origin/master` and holds no work of its own. Between tasks a worktree is parked:
its feature branch is left behind (never deleted — the work lives on in `origin`
and in the branch ref), and the directory returns to its throwaway branch at the
current `origin/master`.

`origin` is `haumacher/phoneblock`. Always name `origin` explicitly.

## Step 1 — Survey the workspace

```bash
git status
git stash list
git worktree list
```

Then, for the currently checked-out branch, find work that exists only locally:

```bash
git log --oneline @{upstream}..HEAD    # unpushed commits (no upstream ⇒ compare to origin/master)
```

Report what you found before changing anything.

## Step 2 — Classify every uncommitted file

Split the dirty/untracked entries into two piles. **Read or diff each file you
are unsure about — never classify by filename alone.**

**Commit** — anything that is source, config, docs, tests, or release notes; in
short, work a reviewer would want. Group related files into commits with proper
messages, following the repository's commit style (`git log` for reference) and
ending each message with the `Co-Authored-By: Claude` trailer required by the
environment.

**Delete** — build output and tool leftovers that were never meant to be
tracked, e.g.:

- `phoneblock-dongle/firmware/sdkconfig.stale-*` — ESP-IDF's backup of a
  regenerated `sdkconfig` (`sdkconfig` itself is git-ignored, the `.stale-*`
  copy is not, so it surfaces as untracked). Confirm it is just a generated
  snapshot (`diff` it against `sdkconfig`) and remove it.
- `build/`, `target/`, scratch scripts, log files, `*.orig`/`*.rej` merge
  debris, editor backups.

Two rules:

- **If a file's pile is genuinely unclear, ask.** Do not guess between
  committing and deleting.
- **Never delete something merely because it is untracked and inconvenient.**
  Untracked source is the most common form of unsaved work.

If a file *should* be ignored permanently rather than deleted every time,
propose the `.gitignore` addition — that change is itself a commit.

Stashes: leave them alone and tell the user they exist. Do not drop or pop a
stash without being asked.

## Step 3 — Verify clean

```bash
git status --porcelain    # must print nothing
```

Do not continue while output remains.

## Step 4 — Determine the throwaway branch

It is the **basename of the worktree directory** — `basename "$(git rev-parse --show-toplevel)"`
(for `/home/haui/devel/pb-1` that is `pb-1`). Do not invent a name and do not
reuse another worktree's.

Two checks before switching:

- `git worktree list` — if that branch is checked out in a *different*
  worktree, the checkout will fail. Stop and ask the user which branch to park
  on.
- `git log --oneline origin/master..<branch>` — if the throwaway branch has
  commits of its own, `git reset --hard` would discard them. Stop, show them,
  and ask. (Normally this is empty; that's what makes the branch throwaway.)

The main worktree (`/home/haui/devel/phoneblock`) parks on `master` itself, not
on a `pb-N` branch.

## Step 5 — Switch, fetch, reset

```bash
git checkout <branch>
git fetch origin --prune --tags
git reset --hard origin/master
```

`--hard` is intended here: the point is to discard local divergence of the
throwaway branch. It is safe **only** because steps 3 and 4 proved there is
nothing to lose.

## Step 6 — Report

Confirm the final state (`git status`, `git log --oneline -3`) and tell the user:

- which commits were created (if any) and whether they still need pushing,
- which files were deleted,
- the branch left behind and that its work is preserved there,
- the commit the workspace now sits at.

If commits were created on the feature branch and are not yet pushed, say so
explicitly — the branch ref keeps them locally, but they exist nowhere else.
Ask whether to push; do not push on your own initiative.
