#!/usr/bin/env bash
# =============================================================================
# issue-next.sh — APPROVED → DONE, nettoie current-issue.md, lance la prochaine
#
# Usage: bash .claude/scripts/issue-next.sh
# Appelé par : Reviewer agent (après APPROVED + commit)
#
# Ne crée pas d'archive. Le fichier source issues/ISSUE-XXX-name.md reste
# l'unique source de vérité — son **Statut** est mis à jour à DONE.
# =============================================================================
set -euo pipefail

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
CURRENT_STATUS=$(grep -oP '\*\*Status\*\*: \K\w+' "$CURRENT")

if [[ "$CURRENT_STATUS" != "APPROVED" ]]; then
    echo "❌ Cannot advance: current status is ${CURRENT_STATUS} (expected APPROVED)"
    exit 1
fi

# ── Update source file **Statut** → DONE ────────────────────────────────────
SOURCE_FILE=$(grep -oP '\*\*IssueFile\*\*: \K.*' "$CURRENT" 2>/dev/null || echo "")
if [[ -z "$SOURCE_FILE" || ! -f "${WORKSPACE}/${SOURCE_FILE}" ]]; then
    echo "❌ current-issue.md missing or invalid **IssueFile** — old format unsupported"
    exit 1
fi
sed -i "s/\*\*Statut\*\*[[:space:]]*:.*/**Statut** : DONE/" "${WORKSPACE}/${SOURCE_FILE}"

# ── APPROVED → DONE in progress.md ──────────────────────────────────────────
sed -i "/^## Issues/,/^## PDRs/{s#| ${ISSUE_ID} | .* | APPROVED |#| ${ISSUE_ID} | ${TITLE} | DONE |#}" "$PROGRESS"
echo "| $(date -I) | ${ISSUE_ID} | APPROVED → DONE | issue-next.sh |" >> "$PROGRESS"

# ── Nettoyer current-issue.md (pas d'archive — le fichier source fait foi) ──
rm "$CURRENT"

# ── Lancer la prochaine ──────────────────────────────────────────────────────
exec "$(dirname "${BASH_SOURCE[0]}")/issue-start.sh"
