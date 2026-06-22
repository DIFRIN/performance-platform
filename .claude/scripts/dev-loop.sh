#!/usr/bin/env bash
# =============================================================================
# dev-loop.sh — Minimal Developer↔Reviewer Loop
#
# The script ONLY handles iteration. Developer and Reviewer know their jobs:
# - Developer: finds next Issue, implements, marks IN REVIEW
# - Reviewer: finds IN REVIEW Issue, reviews, marks DONE or CHANGES_REQUESTED
#
# Each `claude` invocation = fresh context. No manual /clear needed.
# =============================================================================

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PERF_PROJECT_ROOT:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
PROGRESS_FILE="$PROJECT_ROOT/.claude/workspace/progress.md"
DEVELOPER_MD="$PROJECT_ROOT/.claude/agents/developer.md"
REVIEWER_MD="$PROJECT_ROOT/.claude/agents/reviewer.md"

# ─── .env ───────────────────────────────────────────────────────────────────────
[[ -f "$PROJECT_ROOT/.env" ]] && { set -a; source "$PROJECT_ROOT/.env"; set +a; }

# ─── Args ───────────────────────────────────────────────────────────────────────
MAX_ITERATIONS=0
DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --max)     MAX_ITERATIONS="${2:-0}"; shift 2 ;;
    --dry-run) DRY_RUN=true;            shift   ;;
    --help|-h)
      echo "Usage: dev-loop.sh [--max N] [--dry-run]"
      echo "  Developer↔Reviewer loop. Agents follow their own protocols."
      exit 0
      ;;
    *) echo "Unknown: $1"; exit 1 ;;
  esac
done

# ─── Quick status ──────────────────────────────────────────────────────────────

_count() {
  # Return single integer: count of Issues with given status
  local n
  n=$(grep -ciE "^\| *ISSUE-[0-9]+.*\| *${1} *\|" "$PROGRESS_FILE" 2>/dev/null) || n=0
  # Clean: keep only digits, default to 0
  n="${n//[^0-9]/}"
  echo "${n:-0}"
}

status_line() {
  echo "DONE:$(_count DONE) APPROVED:$(_count APPROVED) REVIEW:$(_count 'IN REVIEW') CHANGES:$(_count 'CHANGES_REQUESTED') PROGRESS:$(_count 'IN PROGRESS') WAIT:$(_count WAITING)"
}

has_work() {
  local n
  n=$(( $(_count 'IN REVIEW') + $(_count 'IN PROGRESS') + $(_count WAITING) + $(_count APPROVED) + $(_count 'CHANGES_REQUESTED') ))
  [[ "$n" -gt 0 ]]
}

# ─── Invoke agents ─────────────────────────────────────────────────────────────

invoke_developer() {
  echo "🔨 Developer..."
  claude \
    --add-dir "$PROJECT_ROOT" \
    --dangerously-skip-permissions \
    --system-prompt "$(cat "$DEVELOPER_MD")" \
    -p "Follow your Developer protocol. Read .claude/workspace/current-issue.md. If it doesn't exist, run issue-start.sh. Implement the issue." 2>&1
  echo "🔨 Developer done (exit=$?)"
}

invoke_reviewer() {
  echo "📝 Reviewer..."
  claude \
    --add-dir "$PROJECT_ROOT" \
    --dangerously-skip-permissions \
    --system-prompt "$(cat "$REVIEWER_MD")" \
    -p "Follow your Reviewer protocol. Read .claude/workspace/current-issue.md and review the changes." 2>&1
  echo "📝 Reviewer done (exit=$?)"
}

# ─── Dry Run ────────────────────────────────────────────────────────────────────

if [[ "$DRY_RUN" == true ]]; then
  echo "📊 $(status_line)"
  if has_work; then echo "🔍 Would run: Developer → Reviewer → (loop)"; else echo "✅ All DONE"; fi
  exit 0
fi

# ─── Main Loop ──────────────────────────────────────────────────────────────────

echo "══════ Dev-Loop ══════"
echo "📊 $(status_line)"
echo ""

ITER=0

while true; do
  ITER=$((ITER + 1))
  [[ "$MAX_ITERATIONS" -gt 0 && "$ITER" -gt "$MAX_ITERATIONS" ]] && { echo "✅ Max iterations ($MAX_ITERATIONS)"; break; }

  echo "─── Iter $ITER ($(date +%H:%M:%S)) ───"

  # ── Develop ──
  invoke_developer

  # ── Review ──
  invoke_reviewer

  # ── Status ──
  echo "📊 $(status_line)"

  # ── Check if more work ──
  if ! has_work; then
    echo "✅ All Issues DONE!"
    break
  fi

  echo ""
done

echo "══════ Done ($ITER iterations) ══════"
echo "📊 $(status_line)"
