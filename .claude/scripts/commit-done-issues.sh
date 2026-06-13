#!/usr/bin/env bash
# =============================================================================
# commit-done-issues.sh
#
# Crée un commit Conventional Commit pour chaque Issue DONE dans progress.md
# qui n'a pas encore de commit dans l'historique git.
#
# Les commits sont créés dans l'ordre d'apparition dans progress.md.
#
# Usage :
#   bash .claude/scripts/commit-done-issues.sh [--dry-run]
#
# Options :
#   --dry-run   Affiche les commits qui seraient créés sans les effectuer.
#
# Format Conventional Commits produit :
#   <type>(<scope>): <description>
#
#   <body>
#
#   Refs: ISSUE-XXX
#
# Mapping type :
#   Titre contient "init", "setup", "scaffold"  → chore
#   PDR module contient "domain", "dsl"          → feat   (core domain)
#   PDR phase PREPARATION / task executor        → feat
#   PDR phase INJECTION / gatling                → feat
#   PDR phase ASSERTION                          → feat
#   PDR module contient "test", "testing"        → test
#   PDR module contient "report", "observab"     → feat
#   PDR module contient "deploy", "docker", "k8s"→ chore
#   Par défaut                                   → feat
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GIT_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"

PROGRESS_FILE="$GIT_ROOT/.claude/progress.md"
ISSUES_DIR="$GIT_ROOT/.claude/issues"
DRY_RUN=false

for arg in "$@"; do
  [[ "$arg" == "--dry-run" ]] && DRY_RUN=true
done

# ─── Helpers ──────────────────────────────────────────────────────────────────

# Safe trimming without xargs (avoids "unmatched single quote" warnings)
safe_trim() {
  tr -d '\r`'"'"'"' | sed -E 's/^[[:space:]]+//;s/[[:space:]]+$//'
}

# Discover files listed in the "Fichiers à Créer" section of an issue file.
# Returns a list of relative paths (one per line) that exist on disk.
# Handles the tree format: directory/ followed by indented ├── file.java
discover_issue_files() {
  local issue_file="$1"
  local in_section=false
  local current_dir=""  # track directory context for indented files
  local -a paths=()

  while IFS= read -r line; do
    # Detect start of "Fichiers à Créer" section
    if echo "$line" | grep -qE '^## Fichiers (à|a) Créer'; then
      in_section=true
      continue
    fi
    # End of section: next ## heading or --- separator
    if $in_section; then
      if echo "$line" | grep -qE '^## |^---'; then
        in_section=false
        continue
      fi
      # Skip the ``` fence lines
      if echo "$line" | grep -qE '^```'; then
        continue
      fi
      # Trim leading whitespace and tree-drawing chars (├── └── │   ├─ └─)
      local trimmed
      trimmed=$(echo "$line" | sed -E 's/^[[:space:]]*[│├└][─]*[[:space:]]*//')
      # If empty after trimming (just tree chars or empty line), skip
      [[ -z "$trimmed" ]] && continue
      # Remove inline comments (— ...)
      trimmed=$(echo "$trimmed" | sed -E 's/[[:space:]]*—.*$//')
      # If the trimmed line ends with /, it's a directory context for subsequent files
      if echo "$trimmed" | grep -q '/$'; then
        current_dir="$trimmed"
        # The directory itself may be a path (e.g. when it contains files)
        # Add the directory if it exists and is tracked
        [[ -d "$GIT_ROOT/$trimmed" ]] && paths+=("$trimmed")
        continue
      fi
      # If it starts with platform- or src/ or is a relative path, it's a file
      if echo "$trimmed" | grep -qE '^(platform-|src/|pom\.xml|\.claude/)'; then
        # If current_dir is set, prepend it
        local full_path
        if [[ -n "$current_dir" ]]; then
          full_path="${current_dir}${trimmed}"
        else
          full_path="$trimmed"
        fi
        if [[ -f "$GIT_ROOT/$full_path" ]] || [[ -d "$GIT_ROOT/$full_path" ]]; then
          paths+=("$full_path")
        fi
      fi
    fi
  done < "$issue_file"

  # Output unique paths
  printf '%s\n' "${paths[@]}" | sort -u
}

# Extract field value from an Issue file: **Field** : value
issue_field() {
  local file="$1" field="$2"
  grep -m1 "^\*\*${field}\*\*" "$file" \
    | sed "s/^\*\*${field}\*\*[[:space:]]*:[[:space:]]*//" \
    | tr -d '\r' \
    | tr -d '`' \
    | safe_trim
}

# Derive conventional commit type from issue file content
commit_type() {
  local issue_file="$1"
  local title module pdr_file pdr_module

  title=$(issue_field "$issue_file" "Titre" 2>/dev/null || head -1 "$issue_file" | sed 's/^# //')
  module=$(issue_field "$issue_file" "Module")
  pdr_id=$(issue_field "$issue_file" "PDR")
  pdr_file="$GIT_ROOT/.claude/pdr/${pdr_id}-*.md"

  # Read PDR module if available
  pdr_module=""
  for f in $pdr_file; do
    [[ -f "$f" ]] && pdr_module=$(grep -m1 "^\*\*Module Maven\*\*" "$f" | sed 's/.*: *//' | tr -d '`' | safe_trim) && break
  done

  local combined="${title,,} ${module,,} ${pdr_module,,}"

  if echo "$combined" | grep -qE "init|setup|scaffold|docker|k8s|kubernetes|deploy|dockerfile|configmap|helm"; then
    echo "chore"
  elif echo "$combined" | grep -qE "test|testcontainer|integration.test|contract.test|e2e"; then
    echo "test"
  elif echo "$combined" | grep -qE "fix|bugfix|hotfix|correct|repair"; then
    echo "fix"
  elif echo "$combined" | grep -qE "refactor|cleanup|clean.up|rename|reorgani"; then
    echo "refactor"
  elif echo "$combined" | grep -qE "doc|readme|javadoc|comment"; then
    echo "docs"
  elif echo "$combined" | grep -qE "observab|metric|trace|log|micrometer|otel"; then
    echo "feat"
  else
    echo "feat"
  fi
}

# Derive scope from PDR id (PDR-001 → pdr-001) or module (platform-domain → domain)
commit_scope() {
  local issue_file="$1"
  local module pdr_id

  module=$(issue_field "$issue_file" "Module")
  pdr_id=$(issue_field "$issue_file" "PDR")

  # Prefer module: strip "platform-" prefix
  if [[ -n "$module" && "$module" != "—" && "$module" != "-" ]]; then
    echo "$module" | sed 's/^`//;s/`$//' | sed 's/^platform-//' | tr -d ' '
  elif [[ -n "$pdr_id" && "$pdr_id" != "—" && "$pdr_id" != "-" ]]; then
    echo "${pdr_id,,}"
  else
    echo "core"
  fi
}

# Extract short description from Issue title (first heading)
commit_description() {
  local issue_file="$1"
  head -5 "$issue_file" \
    | grep "^# ISSUE-[0-9]" \
    | sed 's/^# ISSUE-[0-9][0-9]* — //' \
    | tr '[:upper:]' '[:lower:]' \
    | sed 's/[[:space:]]*$//' \
    | safe_trim
}

# Build commit body from Issue objective
commit_body() {
  local issue_file="$1" issue_id="$2"
  local objective module taille

  objective=$(awk '/^## Objectif/{found=1; next} found && /^---/{exit} found{print}' "$issue_file" \
    | grep -v '^$' | head -2 | tr '\n' ' ' | safe_trim)
  module=$(issue_field "$issue_file" "Module")
  taille=$(issue_field "$issue_file" "Taille")

  echo "Module: ${module:-unknown}"
  [[ -n "$taille" && "$taille" != "—" ]] && echo "Size: $taille"
  [[ -n "$objective" ]] && echo "" && echo "$objective"
  echo ""
  echo "Refs: $issue_id"
}

# ─── Main ─────────────────────────────────────────────────────────────────────

if [[ ! -f "$PROGRESS_FILE" ]]; then
  echo "❌  progress.md not found at $PROGRESS_FILE"
  exit 1
fi

# 1. Extract all DONE issues from progress.md in order
DONE_ISSUES=()
while IFS= read -r line; do
  # Only match Issue table rows (start with | ISSUE-XXX), skip PDR/history rows
  if echo "$line" | grep -qE "^\|\s*ISSUE-[0-9]+\s*\|" && echo "$line" | grep -qiE "\|\s*DONE\s*\|"; then
    issue_id=$(echo "$line" | grep -oE "ISSUE-[0-9]+" | head -1)
    [[ -n "$issue_id" ]] && DONE_ISSUES+=("$issue_id")
  fi
done < "$PROGRESS_FILE"

if [[ ${#DONE_ISSUES[@]} -eq 0 ]]; then
  echo "ℹ️  No DONE issues found in progress.md"
  exit 0
fi

# 2. Find issues that don't have a commit yet
# A commit exists if git log contains [ISSUE-XXX], "Refs: ISSUE-XXX", or "ISSUE-XXX:"
UNCOMMITTED=()
for issue_id in "${DONE_ISSUES[@]}"; do
  committed=$(git -C "$GIT_ROOT" log --format="%s %b" --all \
    | grep -iE "\[$issue_id\]|Refs:[[:space:]]*$issue_id|${issue_id}:" | head -1 || true)
  if [[ -z "$committed" ]]; then
    UNCOMMITTED+=("$issue_id")
  fi
done

if [[ ${#UNCOMMITTED[@]} -eq 0 ]]; then
  echo "✅  All DONE issues already have a commit."
  exit 0
fi

echo "📋  Issues DONE without commit (in order): ${UNCOMMITTED[*]}"
echo ""

# 3. For each uncommitted issue, build and create the commit
for issue_id in "${UNCOMMITTED[@]}"; do
  # Find the issue file
  ISSUE_FILE=""
  for f in "$ISSUES_DIR/${issue_id}-"*.md "$ISSUES_DIR/${issue_id}.md"; do
    [[ -f "$f" ]] && ISSUE_FILE="$f" && break
  done

  if [[ -z "$ISSUE_FILE" ]]; then
    echo "⚠️  $issue_id — issue file not found in $ISSUES_DIR/, skipping"
    continue
  fi

  # Build commit components
  TYPE=$(commit_type "$ISSUE_FILE")
  SCOPE=$(commit_scope "$ISSUE_FILE")
  DESC=$(commit_description "$ISSUE_FILE")
  BODY=$(commit_body "$ISSUE_FILE" "$issue_id")

  # Fallback description if not found
  [[ -z "$DESC" ]] && DESC="implement $issue_id"

  COMMIT_MSG="${TYPE}(${SCOPE}): ${DESC}

${BODY}"

  if [[ "$DRY_RUN" == true ]]; then
    echo "─── DRY RUN — $issue_id ─────────────────────────────"
    echo "$COMMIT_MSG"
    echo ""
  else
    echo "─── Committing $issue_id ────────────────────────────"
    echo "  $TYPE($SCOPE): $DESC"

    # Stage modified tracked files
    git -C "$GIT_ROOT" add -u

    # Discover and stage files listed in the issue's "Fichiers à Créer" section
    DISCOVERED_FILES=$(discover_issue_files "$ISSUE_FILE")
    if [[ -n "$DISCOVERED_FILES" ]]; then
      while IFS= read -r f; do
        if [[ -f "$GIT_ROOT/$f" ]] || [[ -d "$GIT_ROOT/$f" ]]; then
          git -C "$GIT_ROOT" add "$f" 2>/dev/null || true
        fi
      done <<< "$DISCOVERED_FILES"
    fi

    # Also stage tracking files that are always part of an issue delivery
    for tf in ".claude/progress.md" ".claude/session-state.md" ".claude/context/interfaces-registry.md" ".claude/context/decisions-log.md"; do
      [[ -f "$GIT_ROOT/$tf" ]] && git -C "$GIT_ROOT" add "$tf" 2>/dev/null || true
    done

    # Check if there's anything staged
    if git -C "$GIT_ROOT" diff --cached --quiet; then
      echo "  ⚠️  Nothing staged for $issue_id — commit skipped"
      echo "       Stage your files manually then run again, or use: git add <files>"
      continue
    fi

    git -C "$GIT_ROOT" commit -m "$COMMIT_MSG"
    echo "  ✅  Committed"
    echo ""
  fi
done

if [[ "$DRY_RUN" == true ]]; then
  echo "─── End dry run ─────────────────────────────────────"
  echo "Run without --dry-run to create commits."
fi
