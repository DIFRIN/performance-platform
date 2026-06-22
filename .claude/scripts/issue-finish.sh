#!/usr/bin/env bash
# =============================================================================
# issue-finish.sh — Passe l'Issue IN_PROGRESS → IN_REVIEW
#
# Usage: bash .claude/scripts/issue-finish.sh
# Appelé par : Developer agent (quand l'implémentation est finie)
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

if [[ ! -f "$CURRENT" ]]; then
    echo "❌ No current-issue.md found. Run issue-start.sh first."
    exit 1
fi

ISSUE_ID=$(grep -oP '^# \KISSUE-\d+' "$CURRENT")
TITLE=$(grep '^# ' "$CURRENT" | sed 's/^# [A-Z0-9-]*: //')
CURRENT_STATUS=$(grep -oP '\*\*Status\*\*: \K\w+' "$CURRENT")

if [[ "$CURRENT_STATUS" != "IN_PROGRESS" && "$CURRENT_STATUS" != "CHANGES_REQUESTED" ]]; then
    echo "❌ Cannot finish: current status is ${CURRENT_STATUS} (expected IN_PROGRESS or CHANGES_REQUESTED)"
    exit 1
fi

# ── Marquer IN_REVIEW dans progress.md (scoped ## Issues → ## PDRs) ──────────
sed -i "/^## Issues/,/^## PDRs/{s/| ${ISSUE_ID} | .* | ${CURRENT_STATUS} |/| ${ISSUE_ID} | ${TITLE} | IN_REVIEW |/}" "$PROGRESS"

# ── Mettre à jour current-issue.md ───────────────────────────────────────────
sed -i "s/\*\*Status\*\*: ${CURRENT_STATUS}/**Status**: IN_REVIEW/" "$CURRENT"

# ── Historique (append-only) ─────────────────────────────────────────────────
echo "| $(date -I) | ${ISSUE_ID} | ${CURRENT_STATUS} → IN_REVIEW | issue-finish.sh |" >> "$PROGRESS"

echo "✅ ${ISSUE_ID} → IN_REVIEW"
