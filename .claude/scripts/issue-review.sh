#!/usr/bin/env bash
# =============================================================================
# issue-review.sh — Enregistre le verdict du Reviewer
#
# Usage:
#   issue-review.sh APPROVED
#   issue-review.sh CHANGES_REQUESTED "Raison détaillée des changements demandés"
#
# Appelé par : Reviewer agent
#
# Dépendance sur le format de progress.md :
#   Table Issues en format plat : | ISSUE-XXX | Title | STATUS | PDR | Dependencies |
#   sed opère entre ## Issues et ## PDRs uniquement.
# =============================================================================
set -euo pipefail

VERDICT="${1:?Usage: issue-review.sh <APPROVED|CHANGES_REQUESTED> [message]}"
MESSAGE="${2:-}"

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

# ── Update source file **Statut** ────────────────────────────────────────────
SOURCE_FILE=$(grep -oP '\*\*IssueFile\*\*: \K.*' "$CURRENT" 2>/dev/null || echo "")
if [[ -z "$SOURCE_FILE" || ! -f "${WORKSPACE}/${SOURCE_FILE}" ]]; then
    echo "❌ current-issue.md missing or invalid **IssueFile** — old format unsupported"
    exit 1
fi

if [[ "$VERDICT" == "APPROVED" ]]; then
    # ── IN_REVIEW → APPROVED ─────────────────────────────────────────────────
    sed -i "s/\*\*Statut\*\*[[:space:]]*:.*/**Statut** : APPROVED/" "${WORKSPACE}/${SOURCE_FILE}"
    sed -i "/^## Issues/,/^## PDRs/{s#| ${ISSUE_ID} | .* | IN_REVIEW |#| ${ISSUE_ID} | ${TITLE} | APPROVED |#}" "$PROGRESS"
    sed -i 's/\*\*Status\*\*: IN_REVIEW/**Status**: APPROVED/' "$CURRENT"
    echo "| $(date -I) | ${ISSUE_ID} | IN_REVIEW → APPROVED | Reviewer approved |" >> "$PROGRESS"
    echo "✅ ${ISSUE_ID} APPROVED → prêt pour commit + issue-next.sh"

elif [[ "$VERDICT" == "CHANGES_REQUESTED" ]]; then
    # ── IN_REVIEW → CHANGES_REQUESTED ────────────────────────────────────────
    sed -i "s/\*\*Statut\*\*[[:space:]]*:.*/**Statut** : CHANGES_REQUESTED/" "${WORKSPACE}/${SOURCE_FILE}"
    sed -i "/^## Issues/,/^## PDRs/{s#| ${ISSUE_ID} | .* | IN_REVIEW |#| ${ISSUE_ID} | ${TITLE} | CHANGES_REQUESTED |#}" "$PROGRESS"
    sed -i 's/\*\*Status\*\*: IN_REVIEW/**Status**: CHANGES_REQUESTED/' "$CURRENT"

    cat >> "$CURRENT" << INNEREOF

---
## Reviewer Feedback — $(date -Iminutes)
${MESSAGE}
INNEREOF

    echo "| $(date -I) | ${ISSUE_ID} | IN_REVIEW → CHANGES_REQUESTED | ${MESSAGE} |" >> "$PROGRESS"
    echo "⚠️  ${ISSUE_ID} CHANGES_REQUESTED"
    echo "   Le Developer doit relire current-issue.md et appliquer les feedbacks."

else
    echo "❌ Invalid verdict: $VERDICT (use APPROVED or CHANGES_REQUESTED)"
    exit 1
fi
