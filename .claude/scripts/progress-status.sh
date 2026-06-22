#!/usr/bin/env bash
# =============================================================================
# progress-status.sh — Résumé 1 ligne de l'avancement (0 token IA)
#
# Usage: bash .claude/scripts/progress-status.sh
# Output: DONE:95 APPROVED:0 IN_REVIEW:0 IN_PROGRESS:0 CHANGES_REQUESTED:0 WAITING:0 BLOCKED:0
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROGRESS="$ROOT/workspace/progress.md"

for status in DONE APPROVED IN_REVIEW IN_PROGRESS CHANGES_REQUESTED WAITING BLOCKED; do
    count=$(grep -cP "^\| ISSUE-\d+ \| .* \| ${status} \|" "$PROGRESS" 2>/dev/null || echo 0)
    printf "%s:%s " "$status" "$count"
done
echo
