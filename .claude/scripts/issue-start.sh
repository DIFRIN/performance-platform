#!/usr/bin/env bash
# =============================================================================
# issue-start.sh — Prépare ou reprend une Issue pour le Developer
#
# Usage:
#   issue-start.sh                 Auto-détecte (CHANGES_REQUESTED > WAITING)
#   issue-start.sh ISSUE-042       Démarre/Reprend l'Issue spécifiée
#   issue-start.sh --dry-run       Affiche ce qui serait démarré (sans modifier)
#   issue-start.sh --dry-run ISSUE-042
#
# Scénarios :
#   1. current-issue.md absent → trouve la 1ère WAITING (deps DONE), crée current-issue.md
#   2. current-issue.md CHANGES_REQUESTED → reprend, met IN_PROGRESS
#   3. current-issue.md IN_PROGRESS → resume (déjà actif)
#   4. --dry-run → affiche l'Issue qui serait démarrée sans modifier l'état
#
# Appelé par : Developer agent (jamais l'humain directement)
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKSPACE="$ROOT/workspace"
PROGRESS="$WORKSPACE/progress.md"
CURRENT="$WORKSPACE/current-issue.md"

# ── Args ───────────────────────────────────────────────────────────────────────
DRY_RUN=false
ISSUE_ARG=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        --help|-h)
            echo "Usage: issue-start.sh [--dry-run] [ISSUE-XXX]"
            echo "  --dry-run  Show what would start without modifying state"
            exit 0
            ;;
        ISSUE-*) ISSUE_ARG="$1"; shift ;;
        *) echo "Unknown: $1"; exit 1 ;;
    esac
done

# ── Helper: extraire les dépendances d'une Issue depuis progress.md ─────────────
get_deps() {
    local issue_id="$1"
    # Extrait la colonne Dependencies (5ème colonne) de la table Issues
    sed -n '/^## Issues/,/^## PDRs/p' "$PROGRESS" \
        | grep -P "^\| ${issue_id} \|" \
        | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $6); print $6}' \
        | tr -d ' '
}

# ── Helper: vérifier qu'un statut est bien dans la table Issues ─────────────────
is_status_in_table() {
    local issue_id="$1"
    local expected_status="$2"
    sed -n '/^## Issues/,/^## PDRs/p' "$PROGRESS" \
        | grep -qP "^\| ${issue_id} \| .* \| ${expected_status} \|"
}

# ── Helper: vérifier que toutes les dépendances d'une Issue sont DONE ────────────
deps_are_done() {
    local issue_id="$1"
    local deps
    deps=$(get_deps "$issue_id")

    # Pas de dépendances → OK (accepte tiret simple, em-dash, ou vide)
    if [[ -z "$deps" || "$deps" == "-" || "$deps" == "—" ]]; then
        return 0
    fi

    # Vérifier chaque dépendance
    local IFS=','
    for dep in $deps; do
        dep=$(echo "$dep" | tr -d ' ')  # trim
        [[ -z "$dep" ]] && continue
        # Normalize: if dep doesn't start with ISSUE-, add the prefix
        if [[ ! "$dep" =~ ^ISSUE- ]]; then
            dep="ISSUE-${dep}"
        fi
        if ! is_status_in_table "$dep" "DONE"; then
            return 1
        fi
    done
    return 0
}

# ── Helper: trouver la prochaine Issue à démarrer ───────────────────────────────
find_next_issue() {
    # Extraire la table Issues une seule fois
    local table
    table=$(sed -n '/^## Issues/,/^## PDRs/p' "$PROGRESS")

    # Priorité 1 : CHANGES_REQUESTED (reprise après feedback Reviewer)
    local changed
    changed=$(echo "$table" | grep 'CHANGES_REQUESTED' | grep -oP '^\| \KISSUE-\d+' | head -1)
    if [[ -n "$changed" ]]; then
        echo "$changed"
        return 0
    fi

    # Priorité 2 : première WAITING dont toutes les dépendances sont DONE
    local waiting_ids
    waiting_ids=$(echo "$table" | grep 'WAITING' | grep -oP '^\| \KISSUE-\d+')
    for id in $waiting_ids; do
        if deps_are_done "$id"; then
            echo "$id"
            return 0
        fi
    done

    # Rien trouvé
    return 1
}

# ── Helper: extraire les métadonnées d'une Issue ────────────────────────────────
extract_metadata() {
    local issue_file="$1"
    TITLE=$(grep '^# ' "$issue_file" | head -1 | sed -E 's/^# [A-Z0-9-]+[[:space:]]*[-–—:][[:space:]]*//')
    PDR=$(grep -oP '\*\*PDR\*\*[[:space:]]*:[[:space:]]*\K[^\s`]+' "$issue_file" | head -1 | tr -d '`' || echo "UNKNOWN")
    MODULE=$(grep -oP 'platform-[a-z-]+' "$issue_file" | head -1 || echo "UNKNOWN")
}

# ── Helper: escape a string for sed s/// replacement (RHS) ────────────────────────
escape_sed_rhs() {
    local s="$1"
    s="${s//\\/\\\\}"   # escape backslash first
    s="${s//&/\\&}"     # escape ampersand (special in replacement)
    s="${s//\//\\/}"    # escape forward slash (delimiter)
    echo "$s"
}

# ── Helper: update **Statut** field in the source issue file ────────────────────────
update_source_status() {
    local issue_file="$1"
    local new_status="$2"
    if [[ -f "$issue_file" ]]; then
        sed -i "s/\*\*Statut\*\*[[:space:]]*:.*/**Statut** : ${new_status}/" "$issue_file"
    fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# Scénario 3 : Reprise IN_PROGRESS ou CHANGES_REQUESTED (current-issue.md existe)
# ═══════════════════════════════════════════════════════════════════════════════
if [[ -f "$CURRENT" ]]; then
    CURRENT_STATUS=$(grep -oP '\*\*Status\*\*: \K\w+' "$CURRENT" 2>/dev/null || echo "")
    CURRENT_ISSUE=$(grep -oP '^# \KISSUE-\d+' "$CURRENT" 2>/dev/null || echo "")

    case "$CURRENT_STATUS" in
        IN_PROGRESS)
            # Si un arg explicite est donné pour une autre Issue
            if [[ -n "$ISSUE_ARG" && "$ISSUE_ARG" != "$CURRENT_ISSUE" ]]; then
                echo "⚠️  current-issue.md has ${CURRENT_ISSUE} (IN_PROGRESS), but you requested ${ISSUE_ARG}"
                echo "   Finish or block ${CURRENT_ISSUE} first, then re-run with ${ISSUE_ARG}"
                exit 1
            fi
            echo "📌 ${CURRENT_ISSUE} already IN_PROGRESS — resuming"
            echo "   current-issue.md is ready"
            exit 0
            ;;

        CHANGES_REQUESTED)
            # Si un arg explicite est donné pour une autre Issue
            if [[ -n "$ISSUE_ARG" && "$ISSUE_ARG" != "$CURRENT_ISSUE" ]]; then
                echo "⚠️  current-issue.md has ${CURRENT_ISSUE} (CHANGES_REQUESTED), but you requested ${ISSUE_ARG}"
                echo "   Finish the review cycle for ${CURRENT_ISSUE} first (fix → issue-finish.sh),"
                echo "   or archive it with issue-next.sh if APPROVED."
                exit 1
            fi

            ISSUE_ID="$CURRENT_ISSUE"
            TITLE=$(grep '^# ' "$CURRENT" | sed 's/^# [A-Z0-9-]*: //')

            if [[ "$DRY_RUN" == true ]]; then
                echo "🔍 DRY-RUN: Would resume ${ISSUE_ID} (CHANGES_REQUESTED → IN_PROGRESS)"
                echo "   Title: ${TITLE}"
                echo "   Action: apply Reviewer feedback, then issue-finish.sh"
                exit 0
            fi

            # ── Mettre à jour status dans source + current-issue.md ────────
            SOURCE_FILE=$(grep -oP '\*\*IssueFile\*\*: \K.*' "$CURRENT" 2>/dev/null || echo "")
            if [[ -n "$SOURCE_FILE" ]]; then
                update_source_status "${WORKSPACE}/${SOURCE_FILE}" "IN_PROGRESS"
            fi
            sed -i 's/\*\*Status\*\*: CHANGES_REQUESTED/**Status**: IN_PROGRESS/' "$CURRENT"

            # ── Marquer IN_PROGRESS dans progress.md ───────────────────────
            TITLE_ESC=$(escape_sed_rhs "$TITLE")
            sed -i "/^## Issues/,/^## PDRs/{s/| ${ISSUE_ID} | .* | CHANGES_REQUESTED |/| ${ISSUE_ID} | ${TITLE_ESC} | IN_PROGRESS |/}" "$PROGRESS"
            echo "| $(date -I) | ${ISSUE_ID} | CHANGES_REQUESTED → IN_PROGRESS (resume) | issue-start.sh |" >> "$PROGRESS"

            echo "♻️  ${ISSUE_ID} CHANGES_REQUESTED → IN_PROGRESS"
            echo "   📋 Reviewer feedback preserved in current-issue.md"
            echo "   Apply fixes, then run issue-finish.sh"
            ;;

        *)
            # Statut inattendu (APPROVED, DONE, BLOCKED...)
            if [[ -n "$ISSUE_ARG" ]]; then
                echo "⚠️  current-issue.md has ${CURRENT_ISSUE} (${CURRENT_STATUS}) — discarding and starting ${ISSUE_ARG}"
                if [[ "$DRY_RUN" == true ]]; then
                    echo "🔍 DRY-RUN: Would discard ${CURRENT_ISSUE} and start ${ISSUE_ARG}"
                    exit 0
                fi
                # Discard old current-issue.md (no archive — source file is the record)
                rm "$CURRENT"
                echo "   Discarded previous current-issue.md"
                # Continuer vers Scénario 1 ci-dessous
            else
                echo "❌ current-issue.md has status '${CURRENT_STATUS}' — cannot start"
                echo "   Run issue-next.sh first if APPROVED/DONE, or check manually."
                exit 1
            fi
            ;;
    esac

    # Si on est arrivé ici via le cas "discard et continuer", on ne fait pas exit
    if [[ "$CURRENT_STATUS" == "IN_PROGRESS" || "$CURRENT_STATUS" == "CHANGES_REQUESTED" ]]; then
        exit 0
    fi
    # Sinon (statut inattendu + arg explicite), on continue vers Scénario 1
fi

# ═══════════════════════════════════════════════════════════════════════════════
# Scénario 1 : Nouvelle Issue
# ═══════════════════════════════════════════════════════════════════════════════

# ── Déterminer l'Issue ID ──────────────────────────────────────────────────────
if [[ -n "$ISSUE_ARG" ]]; then
    ISSUE_ID="$ISSUE_ARG"
    # Vérifier que l'Issue existe dans progress.md
    if ! grep -qP "^\| ${ISSUE_ID} \|" "$PROGRESS"; then
        echo "❌ ${ISSUE_ID} not found in progress.md"
        exit 1
    fi
    # Vérifier le statut
    if ! is_status_in_table "$ISSUE_ID" "WAITING" && ! is_status_in_table "$ISSUE_ID" "CHANGES_REQUESTED"; then
        ACTUAL_STATUS=$(sed -n '/^## Issues/,/^## PDRs/p' "$PROGRESS" \
            | grep -P "^\| ${ISSUE_ID} \|" \
            | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $4); print $4}')
        echo "❌ ${ISSUE_ID} has status '${ACTUAL_STATUS}' (expected WAITING or CHANGES_REQUESTED)"
        exit 1
    fi
    # Vérifier les dépendances
    if is_status_in_table "$ISSUE_ID" "WAITING" && ! deps_are_done "$ISSUE_ID"; then
        DEPS=$(get_deps "$ISSUE_ID")
        echo "❌ ${ISSUE_ID} dependencies not all DONE: ${DEPS}"
        echo "   Run: bash .claude/scripts/progress-status.sh to check status"
        exit 1
    fi
else
    ISSUE_ID=$(find_next_issue) || true

    if [[ -z "$ISSUE_ID" ]]; then
        if [[ "$DRY_RUN" == true ]]; then
            echo "🔍 DRY-RUN: No WAITING issues with all dependencies DONE."
            echo "   Run progress-status.sh to see current state."
            exit 0
        fi
        echo "✅ No startable issues remaining."
        {
            echo "# No active issue — all work complete"
            echo "**Status**: DONE"
            echo "**Date**: $(date -Iminutes)"
            echo ""
            echo "Run \`progress-status.sh\` to verify."
        } > "$CURRENT"
        exit 0
    fi

    # Safety: if auto-detect returned CHANGES_REQUESTED but current-issue.md is
    # missing, the Reviewer feedback was lost. Refuse to create a clean slate.
    if is_status_in_table "$ISSUE_ID" "CHANGES_REQUESTED"; then
        echo "❌ ${ISSUE_ID} is CHANGES_REQUESTED but current-issue.md is missing."
        echo "   Reviewer feedback would be destroyed. Restore current-issue.md or"
        echo "   manually resolve with: issue-review.sh APPROVED"
        exit 1
    fi
fi

ISSUE_FILE=$(ls "$WORKSPACE/issues/${ISSUE_ID}"*.md 2>/dev/null | head -1)

if [[ ! -f "$ISSUE_FILE" ]]; then
    echo "❌ Issue file not found: $WORKSPACE/issues/${ISSUE_ID}*.md"
    exit 1
fi

# ── Extraire métadonnées ─────────────────────────────────────────────────────
extract_metadata "$ISSUE_FILE"

# ── Dry-run : afficher sans modifier ──────────────────────────────────────────
if [[ "$DRY_RUN" == true ]]; then
    DEPS=$(get_deps "$ISSUE_ID")
    echo "🔍 DRY-RUN: Would start ${ISSUE_ID}"
    echo "   Title    : ${TITLE}"
    echo "   PDR      : ${PDR}"
    echo "   Module   : ${MODULE}"
    echo "   Deps     : ${DEPS:--}"
    if [[ -n "$DEPS" && "$DEPS" != "-" ]]; then
        echo "   Deps status:"
        old_ifs="$IFS"
        IFS=','
        for dep in $DEPS; do
            IFS="$old_ifs"
            dep=$(echo "$dep" | tr -d ' ')
            [[ -z "$dep" ]] && continue
            if is_status_in_table "$dep" "DONE"; then
                echo "     ✅ ${dep} DONE"
            else
                DEP_STAT=$(sed -n '/^## Issues/,/^## PDRs/p' "$PROGRESS" \
                    | grep -P "^\| ${dep} \|" \
                    | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $4); print $4}')
                echo "     ❌ ${dep} ${DEP_STAT:-UNKNOWN}"
            fi
        done
        IFS="$old_ifs"
    fi
    echo "   Action   : mark IN_PROGRESS, create current-issue.md"
    exit 0
fi

# ── Marquer IN_PROGRESS dans progress.md ────────────────────────────────────
OLD_STATUS=$(sed -n '/^## Issues/,/^## PDRs/p' "$PROGRESS" \
    | grep -P "^\| ${ISSUE_ID} \|" \
    | awk -F'|' '{gsub(/^[[:space:]]+|[[:space:]]+$/, "", $4); print $4}')

# Guard: if OLD_STATUS is empty, the sed pattern would be malformed
if [[ -z "$OLD_STATUS" ]]; then
    echo "❌ Could not extract status for ${ISSUE_ID} from progress.md"
    echo "   Check the Issues table row format."
    exit 1
fi

TITLE_ESC=$(escape_sed_rhs "$TITLE")
sed -i "/^## Issues/,/^## PDRs/{s/| ${ISSUE_ID} | .* | ${OLD_STATUS} |/| ${ISSUE_ID} | ${TITLE_ESC} | IN_PROGRESS |/}" "$PROGRESS"
echo "| $(date -I) | ${ISSUE_ID} | ${OLD_STATUS} → IN_PROGRESS | issue-start.sh |" >> "$PROGRESS"

# ── Update source file status ──────────────────────────────────────────────
update_source_status "$ISSUE_FILE" "IN_PROGRESS"

# ── Créer current-issue.md (pointer — body stays in issues/ISSUE-XXX.md) ────
ISSUE_FILE_REL="issues/$(basename "$ISSUE_FILE")"

cat > "$CURRENT" << INNEREOF
# ${ISSUE_ID}: ${TITLE}
**Status**: IN_PROGRESS
**PDR**: ${PDR}
**Module**: ${MODULE}
**Started**: $(date -Iminutes)
**IssueFile**: ${ISSUE_FILE_REL}

> 📄 Full specification: \`.claude/workspace/${ISSUE_FILE_REL}\`

## Reviewer Feedback
(None yet)
INNEREOF

echo "✅ ${ISSUE_ID} → IN_PROGRESS | current-issue.md ready"
echo "   Module: ${MODULE} | PDR: ${PDR}"
