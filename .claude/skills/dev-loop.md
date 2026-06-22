---
name: dev-loop
description: Simple Developer↔Reviewer loop. Each iteration = fresh context via claude subprocess. Invoke with /dev-loop or bash .claude/scripts/dev-loop.sh
category: workflow
tools: Bash, Read, Write, Edit, Agent
---

# /dev-loop — Developer↔Reviewer Loop

> **Simple iterations: Developer → Reviewer → (fix) → Reviewer.**
> Each iteration = fresh `claude` subprocess = automatic context clearing.

---

## Quick Start

```bash
# Full loop (all WAITING Issues)
bash .claude/scripts/dev-loop.sh

# Limited run
bash .claude/scripts/dev-loop.sh --max 5
bash .claude/scripts/dev-loop.sh --pdr PDR-020

# Dry-run (see what would happen)
bash .claude/scripts/dev-loop.sh --dry-run
```

---

## Architecture

```
┌──────────────────────────────────────────────────────┐
│              dev-loop.sh (BASH LOOP)                 │
│                                                      │
│  Each iteration = NEW claude process = FRESH CONTEXT │
│                                                      │
│  ┌───────────┐    ┌───────────┐                     │
│  │ DEVELOPER │───▶│ REVIEWER  │──▶ DONE + COMMIT    │
│  │ (claude)  │    │ (claude)  │                     │
│  └───────────┘    └───────────┘                     │
│       │                 │                           │
│       │    CHANGES_REQUESTED                        │
│       └──────▶ DEVELOPER (fix) ──▶ REVIEWER        │
│                                                      │
│  State in: .claude/progress.md                       │
│  Recommendations in: .claude/context/recommendations-tracking.md │
└──────────────────────────────────────────────────────┘
```

---

## Flow

1. **Detect** — Bash reads `progress.md` to find next action
2. **Develop** — `claude` subprocess with Developer prompt → marks IN REVIEW
3. **Bash verifies** — Checks that `progress.md` was actually updated
4. **Review** — `claude` subprocess with Reviewer prompt → APPROVED or CHANGES_REQUESTED
5. **Bash verifies** — Checks status change
6. **If CHANGES_REQUESTED** — Bash invokes Developer in "fix" mode → marks IN REVIEW → Reviewer re-reviews
7. **Repeat** — Until all Issues DONE or max iterations hit

---

## Key Design

| Decision | Rationale |
|---|---|
| Bash-driven loop | Each `claude` = fresh context, no accumulation |
| State via progress.md | Stateless between invocations, resumable |
| Developer + Reviewer only | No Tester in main loop, no separate Fixer role |
| Fix = Developer mode | CHANGES_REQUESTED → Developer applies fixes → Reviewer re-reviews |
| Bash verification | After each `claude` call, verify `progress.md` actually changed |
| 3 consecutive fails → stop | Prevent infinite loops when `claude` is broken |

---

## Context Management

Each `claude` subprocess starts with **zero context** — it only reads the files it needs (progress.md, issue file, source code). No manual `/clear` needed. The bash loop itself has no context limit (it's just bash).

---

## Files

| File | Role |
|---|---|
| `.claude/scripts/dev-loop.sh` | Bash loop driver |
| `.claude/agents/developer.md` | Developer system prompt |
| `.claude/agents/reviewer.md` | Reviewer system prompt |
| `.claude/skills/dev-loop.md` | This documentation |
