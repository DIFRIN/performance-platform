#!/usr/bin/env bash
# =============================================================================
# progress-status.sh — Résumé 1 ligne de l'avancement (0 token IA)
#
# Usage: bash .claude/scripts/progress-status.sh
# Output: DONE:95 APPROVED:0 IN_REVIEW:0 IN_PROGRESS:0 CHANGES_REQUESTED:0 WAITING:0 BLOCKED:0
#
# Compte UNIQUEMENT dans la table Issues (entre ## Issues et ## PDRs),
# jamais dans l'historique.
#
# Dépendance sur le format de progress.md :
#   Table Issues en format plat : | ISSUE-XXX | Title | STATUS | PDR | Dependencies |
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROGRESS="$ROOT/workspace/progress.md"

# Extract only the Issues table (between ## Issues and ## PDRs)
ISSUES_TABLE=$(sed -n '/^## Issues/,/^## PDRs/p' "$PROGRESS")

for status in DONE APPROVED IN_REVIEW IN_PROGRESS CHANGES_REQUESTED WAITING BLOCKED; do
    count=$(echo "$ISSUES_TABLE" | grep -cP "^\| ISSUE-\d+ \| .* \| ${status} \|" 2>/dev/null | tr -d '\n' || true)
    printf "%s:%s " "$status" "${count:-0}"
done
echo
