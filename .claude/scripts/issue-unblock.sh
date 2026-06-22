#!/usr/bin/env bash
# =============================================================================
# issue-unblock.sh — Débloque une Issue (BLOCKED → WAITING ou IN_PROGRESS)
#
# Usage:
#   issue-unblock.sh                    Débloque l'Issue dans current-issue.md
#   issue-unblock.sh ISSUE-042          Débloque l'Issue spécifiée
#   issue-unblock.sh --resume           BLOCKED → IN_PROGRESS (reprend le travail)
#   issue-unblock.sh --resume ISSUE-042
#
# Appelé par : Developer ou Architect (quand le blocage est levé)
#
# Dépendance sur le format de progress.md :
#   Table Issues en format plat : | ISSUE-XXX | Title | STATUS | PDR | Dependencies |
#   sed opère entre ## Issues et ## PDRs uniquement.
# =============================================================================
set -euo pipefail

RESUME=false
ISSUE_ARG=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --resume) RESUME=true; shift ;;
        --help|-h)
            echo "Usage: issue-unblock.sh [--resume] [ISSUE-XXX]"
            echo "  --resume  BLOCKED → IN_PROGRESS (resume work)"
            echo "  default   BLOCKED → WAITING (return to queue)"
            exit 0
            ;;
        ISSUE-*) ISSUE_ARG="$1"; shift ;;
        *) echo "Unknown: $1"; exit 1 ;;
    esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$ROOT/workspace"
CURRENT="$WORKSPACE/current-issue.md"
PROGRESS="$WORKSPACE/progress.md"

# ── Déterminer l'Issue ID ──────────────────────────────────────────────────────
if [[ -n "$ISSUE_ARG" ]]; then
    ISSUE_ID="$ISSUE_ARG"
    # Trouver le titre depuis progress.md
    ISSUE_LINE=$(sed -n '/^## Issues$/,/^## PDRs$/p' "$PROGRESS" | grep -P "^\| ${ISSUE_ID} \|")
    if [[ -z "$ISSUE_LINE" ]]; then
        echo "❌ ${ISSUE_ID} not found in progress.md"
        exit 1
    fi
    TITLE=$(echo "$ISSUE_LINE" | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $3); print $3}')
    CURRENT_STATUS=$(echo "$ISSUE_LINE" | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $4); print $4}')
else
    if [[ ! -f "$CURRENT" ]]; then
        echo "❌ No current-issue.md found. Specify ISSUE-XXX or run issue-start.sh first."
        exit 1
    fi
    ISSUE_ID=$(grep -oP '^# \KISSUE-\d+' "$CURRENT")
    TITLE=$(grep '^# ' "$CURRENT" | sed 's/^# [A-Z0-9-]*: //')
    CURRENT_STATUS=$(grep -oP '\*\*Status\*\*: \K\w+' "$CURRENT")
fi

# ── Valider le statut actuel ───────────────────────────────────────────────────
if [[ "$CURRENT_STATUS" != "BLOCKED" ]]; then
    echo "❌ ${ISSUE_ID} is '${CURRENT_STATUS}', not BLOCKED"
    exit 1
fi

# ── Déterminer le nouveau statut ───────────────────────────────────────────────
if [[ "$RESUME" == true ]]; then
    NEW_STATUS="IN_PROGRESS"
    echo "🔓 ${ISSUE_ID} BLOCKED → IN_PROGRESS (resuming work)"
else
    NEW_STATUS="WAITING"
    echo "🔓 ${ISSUE_ID} BLOCKED → WAITING (returned to queue)"
fi

# ── Mettre à jour progress.md ──────────────────────────────────────────────────
sed -i "/^## Issues$/,/^## PDRs$/{s/| ${ISSUE_ID} | .* | BLOCKED |/| ${ISSUE_ID} | ${TITLE} | ${NEW_STATUS} |/}" "$PROGRESS"
echo "| $(date -I) | ${ISSUE_ID} | BLOCKED → ${NEW_STATUS} | issue-unblock.sh |" >> "$PROGRESS"

# ── Mettre à jour current-issue.md si présent ──────────────────────────────────
if [[ -f "$CURRENT" ]]; then
    CURRENT_ISSUE=$(grep -oP '^# \KISSUE-\d+' "$CURRENT" 2>/dev/null || echo "")
    if [[ "$CURRENT_ISSUE" == "$ISSUE_ID" ]]; then
        sed -i "s/\*\*Status\*\*: BLOCKED/**Status**: ${NEW_STATUS}/" "$CURRENT"
        # Retirer le bloc "Blocked"
        sed -i '/^## Blocked -/,/^\*\*Reason\*\*:/d' "$CURRENT"
        echo "   current-issue.md updated"
    fi
fi

echo "✅ ${ISSUE_ID} unblocked → ${NEW_STATUS}"
