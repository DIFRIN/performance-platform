#!/usr/bin/env bash
# =============================================================================
# setup-hooks.sh — Installe les git hooks du projet.
# Lancer une seule fois après git clone ou git init.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GIT_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
HOOKS_SRC="$SCRIPT_DIR/git-hooks"
HOOKS_DST="$GIT_ROOT/.git/hooks"

echo "Installing git hooks from $HOOKS_SRC → $HOOKS_DST"

for hook in "$HOOKS_SRC"/*; do
  name="$(basename "$hook")"
  dst="$HOOKS_DST/$name"

  if [[ -f "$dst" ]]; then
    echo "  ⚠️  $name already exists — skipping (remove manually to reinstall)"
  else
    cp "$hook" "$dst"
    chmod +x "$dst"
    echo "  ✅ $name installed"
  fi
done

echo ""
echo "Done. Hooks active for this repo."
