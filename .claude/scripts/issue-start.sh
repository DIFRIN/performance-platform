#!/usr/bin/env bash
# =============================================================================
# issue-start.sh — Démarre une Issue (WAITING → IN_PROGRESS) et crée current-issue.md
#
# Usage:
#   issue-start.sh              Auto-détecte la 1ère WAITING
#   issue-start.sh ISSUE-042    Démarre l'Issue spécifiée
#
# Appelé par : Developer agent (jamais l'humain directement)
#
# Dépendance sur le format de progress.md :
#   Table Issues en format plat : | ISSUE-XXX | Title | STATUS | PDR | Dependencies |
#   Sections ## Issues et ## PDRs délimitent la table (sed opère entre les deux).
#   Section ## History en append-only (>>).
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$ROOT/workspace"
PROGRESS="$WORKSPACE/progress.md"

# ── Résoudre l'Issue ─────────────────────────────────────────────────────────
if [[ $# -ge 1 ]]; then
    ISSUE_ID="$1"
else
    ISSUE_ID=$(grep -oP '^\| \KISSUE-\d+' "$PROGRESS" | while read -r id; do
        if grep -qP "^\| ${id} \| .* \| WAITING \|" "$PROGRESS" 2>/dev/null; then
            echo "$id"
            break
        fi
    done)

    if [[ -z "$ISSUE_ID" ]]; then
        echo "✅ No WAITING issues remaining."
        {
            echo "# No active issue — all work complete"
            echo "**Status**: DONE"
            echo "**Date**: $(date -Iminutes)"
            echo ""
            echo "Run \`progress-status.sh\` to verify."
        } > "$WORKSPACE/current-issue.md"
        exit 0
    fi
fi

ISSUE_FILE="$WORKSPACE/issues/${ISSUE_ID}.md"

if [[ ! -f "$ISSUE_FILE" ]]; then
    echo "❌ Issue file not found: $ISSUE_FILE"
    exit 1
fi

# ── Extraire métadonnées ─────────────────────────────────────────────────────
# Heading format: "# ISSUE-XXX: Title" or "# ISSUE-XXX — Title" (both supported)
TITLE=$(grep '^# ' "$ISSUE_FILE" | head -1 | sed -E 's/^# [A-Z0-9-]+[[:space:]]*[-–—:][[:space:]]*//')
# PDR: extract from **PDR** : PDR-XXX or **PDR** : `PDR-XXX`
PDR=$(grep -oP '\*\*PDR\*\*[[:space:]]*:[[:space:]]*\K[^\s`]+' "$ISSUE_FILE" | head -1 | tr -d '`' || echo "UNKNOWN")
MODULE=$(grep -oP 'platform-[a-z-]+' "$ISSUE_FILE" | head -1 || echo "UNKNOWN")

# ── Marquer IN_PROGRESS dans progress.md (scoped ## Issues → ## PDRs) ────────
# Only the Issues table is modified; history rows are append-only.
# Table format: | ISSUE-XXX | Title | STATUS | PDR | Dependencies |
sed -i "/^## Issues$/,/^## PDRs$/{s/| ${ISSUE_ID} | .* | WAITING |/| ${ISSUE_ID} | ${TITLE} | IN_PROGRESS |/}" "$PROGRESS"

# ── Ajouter entrée historique (append-only) ───────────────────────────────────
echo "| $(date -I) | ${ISSUE_ID} | WAITING → IN_PROGRESS | issue-start.sh |" >> "$PROGRESS"

# ── Créer current-issue.md ───────────────────────────────────────────────────
cat > "$WORKSPACE/current-issue.md" << INNEREOF
# ${ISSUE_ID}: ${TITLE}
**Status**: IN_PROGRESS
**PDR**: ${PDR}
**Module**: ${MODULE}
**Started**: $(date -Iminutes)

$(tail -n +2 "$ISSUE_FILE")

## Reviewer Feedback
(None yet)
INNEREOF

echo "✅ ${ISSUE_ID} → IN_PROGRESS | current-issue.md ready"
echo "   Module: ${MODULE} | PDR: ${PDR}"
