#!/usr/bin/env bash
# =============================================================================
# issue-next.sh — APPROVED → DONE, archive, et lance la prochaine Issue
#
# Usage: bash .claude/scripts/issue-next.sh
# Appelé par : Reviewer agent (après APPROVED + commit)
#
# Dépendance sur le format de progress.md :
#   Table Issues en format plat : | ISSUE-XXX | Title | STATUS | PDR | Dependencies |
#   sed opère entre ## Issues et ## PDRs uniquement.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$ROOT/workspace"
CURRENT="$WORKSPACE/current-issue.md"
PROGRESS="$WORKSPACE/progress.md"
ARCHIVE_DIR="$WORKSPACE/issues/"

if [[ ! -f "$CURRENT" ]]; then
    echo "❌ No current-issue.md found."
    exit 1
fi

ISSUE_ID=$(grep -oP '^# \KISSUE-\d+' "$CURRENT")
TITLE=$(grep '^# ' "$CURRENT" | sed 's/^# [A-Z0-9-]*: //')
CURRENT_STATUS=$(grep -oP '\*\*Status\*\*: \K\w+' "$CURRENT")

if [[ "$CURRENT_STATUS" != "APPROVED" ]]; then
    echo "❌ Cannot advance: current status is ${CURRENT_STATUS} (expected APPROVED)"
    exit 1
fi

# ── APPROVED → DONE (scoped ## Issues → ## PDRs) ─────────────────────────────
sed -i "/^## Issues/,/^## PDRs/{s/| ${ISSUE_ID} | .* | APPROVED |/| ${ISSUE_ID} | ${TITLE} | DONE |/}" "$PROGRESS"
echo "| $(date -I) | ${ISSUE_ID} | APPROVED → DONE | issue-next.sh |" >> "$PROGRESS"

# ── Archiver ─────────────────────────────────────────────────────────────────
# mv (not cp) so current-issue.md is removed — issue-start.sh starts fresh
mv "$CURRENT" "${ARCHIVE_DIR}${ISSUE_ID}-completed.md"
echo "📦 Archived: ${ARCHIVE_DIR}${ISSUE_ID}-completed.md"

# ── Lancer la prochaine ──────────────────────────────────────────────────────
exec "$(dirname "${BASH_SOURCE[0]}")/issue-start.sh"
