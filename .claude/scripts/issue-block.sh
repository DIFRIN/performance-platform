#!/usr/bin/env bash
# =============================================================================
# issue-block.sh — Bloque l'Issue courante
#
# Usage:
#   issue-block.sh "Dépendance ISSUE-099 non satisfaite"
#   issue-block.sh                (sans message)
#
# Appelé par : Developer agent (quand bloqué)
#
# Dépendance sur le format de progress.md :
#   Table Issues en format plat : | ISSUE-XXX | Title | STATUS | PDR | Dependencies |
#   sed opère entre ## Issues et ## PDRs uniquement.
# =============================================================================
set -euo pipefail

REASON="${1:-No reason provided}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$ROOT/workspace"
CURRENT="$WORKSPACE/current-issue.md"
PROGRESS="$WORKSPACE/progress.md"

if [[ ! -f "$CURRENT" ]]; then
    echo "❌ No current-issue.md found."
    exit 1
fi

ISSUE_ID=$(grep -oP '^# \KISSUE-\d+' "$CURRENT")
TITLE=$(grep '^# ' "$CURRENT" | sed 's/^# [A-Z0-9-]*: //')
OLD_STATUS=$(grep -oP '\*\*Status\*\*: \K\w+' "$CURRENT")

# ── Marquer BLOCKED (scoped ## Issues → ## PDRs) ─────────────────────────────
sed -i "/^## Issues$/,/^## PDRs$/{s/| ${ISSUE_ID} | .* | ${OLD_STATUS} |/| ${ISSUE_ID} | ${TITLE} | BLOCKED |/}" "$PROGRESS"
sed -i "s/\*\*Status\*\*: ${OLD_STATUS}/**Status**: BLOCKED/" "$CURRENT"

cat >> "$CURRENT" << INNEREOF

---
## Blocked — $(date -Iminutes)
**Reason**: ${REASON}
INNEREOF

echo "| $(date -I) | ${ISSUE_ID} | ${OLD_STATUS} → BLOCKED | ${REASON} |" >> "$PROGRESS"

echo "🚫 ${ISSUE_ID} BLOCKED: ${REASON}"
