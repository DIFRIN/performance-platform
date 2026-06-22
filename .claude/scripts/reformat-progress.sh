#!/usr/bin/env bash
# =============================================================================
# reformat-progress.sh — Convertit progress.md au nouveau format sed-friendly
#
# Usage: bash .claude/scripts/reformat-progress.sh
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROGRESS="$ROOT/workspace/progress.md"
BACKUP="$ROOT/workspace/progress.md.bak-$(date +%Y%m%d-%H%M%S)"

echo "📦 Backup: $BACKUP"
cp "$PROGRESS" "$BACKUP"

# ── Extraire les Issues (table unique, format sed-friendly) ──────────────────
# Input:  | ISSUE-XXX | Titre | PDR-XXX | module | taille | Statut | deps |
# Output: | ISSUE-XXX | Titre | Statut | PDR-XXX | deps |
ISSUES=$(awk '
/^\| ISSUE-[0-9]+/ {
  n=split($0, a, "|")
  id=a[2]; gsub(/^ +| +$/, "", id)
  status=a[n-2]; gsub(/^ +| +$/, "", status)
  deps=a[n-1]; gsub(/^ +| +$/, "", deps)
  if(deps=="—"||deps=="") deps="-"
  pdr="UNKNOWN"
  for(i=3; i<=n; i++) if(a[i] ~ /PDR-[0-9]+/) { gsub(/^ +| +$/, "", a[i]); pdr=a[i]; break }
  title=""
  for(i=3; i<=n; i++) { if(a[i] ~ /PDR-[0-9]+/) break; gsub(/^ +| +$/, "", a[i]); if(a[i]!="") title=title a[i] " " }
  gsub(/^ +| +$/, "", title)
  printf "| %s | %s | %s | %s | %s |\n", id, title, status, pdr, deps
}
' "$PROGRESS")

# ── Extraire les PDRs ────────────────────────────────────────────────────────
PDRS=$(awk '/^## PDRs$/,/^---$/' "$PROGRESS" | grep '^| PDR-[0-9]' || true)

# ── Extraire l historique ────────────────────────────────────────────────────
HISTORY=$(awk '
/^\| Date \| Item \| Transition \| Agent \|$/,0 {
  if (/^\| 20[0-9]{2}-[0-9]{2}-[0-9]{2}/) print
}
' "$PROGRESS")

# ── Assembler ─────────────────────────────────────────────────────────────────
{
  echo '# Progress'
  echo ''
  echo '> NE PAS CHARGER EN CONTEXTE IA. Modifié uniquement par les scripts `.claude/scripts/issue-*.sh`.'
  echo '> Pour voir l''état : `bash .claude/scripts/progress-status.sh`'
  echo ''
  echo '## Issues'
  echo '| ID | Title | Status | PDR | Dependencies |'
  echo '|----|-------|--------|-----|--------------|'
  echo "$ISSUES"
  echo ''
  echo '## PDRs'
  echo '| ID | Name | Module | Status | Issues | Deps |'
  echo '|----|------|--------|--------|--------|------|'
  echo "$PDRS"
  echo ''
  echo '## History'
  echo '| Date | Issue | Transition | Note |'
  echo '|------|-------|------------|------|'
  echo "$HISTORY"
} > "$PROGRESS"

echo "✅ Nouveau format écrit ($(echo "$ISSUES" | wc -l) issues, $(echo "$PDRS" | wc -l) PDRs)"
echo "   Backup: $BACKUP"
