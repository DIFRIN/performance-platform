#!/usr/bin/env bash
# =============================================================================
# auto-commit-done.sh — Hook PostToolUse
#
# Se déclenche UNIQUEMENT si :
#   1. Le fichier modifié est progress.md
#   2. Cette modification introduit un statut DONE (transition IN REVIEW → DONE)
#
# Entrée (stdin) : JSON Claude Code hook
#   Edit  → { tool_name, tool_input: { file_path, old_string, new_string } }
#   Write → { tool_name, tool_input: { file_path, content } }
# =============================================================================

set -uo pipefail

# Lire le payload JSON depuis stdin et le stocker dans une variable d'env
# (évite le conflit stdin/heredoc pour python3)
CLAUDE_HOOK_JSON=$(cat 2>/dev/null || echo "{}")
export CLAUDE_HOOK_JSON

# ─── Détection via Python3 ───────────────────────────────────────────────────
# Retourne le chemin de progress.md si et seulement si une transition → DONE
# est détectée dans cette édition. Sinon, ne produit aucune sortie.
DETECTED_FILE=$(python3 - << 'PYEOF'
import os, json, re, subprocess

try:
    data = json.loads(os.environ.get("CLAUDE_HOOK_JSON", "{}"))
except Exception:
    exit(0)

tool_name  = data.get("tool_name", "")
tool_input = data.get("tool_input", {})
file_path  = tool_input.get("file_path", "")

# ── Filtre 1 : doit être progress.md ──────────────────────────────────────
if "progress.md" not in file_path:
    exit(0)

done_re = re.compile(r"\|\s*DONE\s*\|")
introduced = False

# ── Filtre 2 : transition → DONE dans cette édition ───────────────────────
if tool_name == "Edit":
    # DONE introduit si new_string le contient et old_string ne le contenait pas
    new_str = tool_input.get("new_string", "")
    old_str = tool_input.get("old_string", "")
    introduced = bool(done_re.search(new_str)) and not bool(done_re.search(old_str))

elif tool_name == "Write":
    # Pour un Write complet : inspecter git diff HEAD pour les lignes ajoutées
    try:
        root = subprocess.run(
            ["git", "-C", os.path.dirname(os.path.abspath(file_path)),
             "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, check=True
        ).stdout.strip()
        diff = subprocess.run(
            ["git", "-C", root, "diff", "HEAD", "--", ".claude/progress.md"],
            capture_output=True, text=True
        ).stdout
        added_lines = [
            line[1:] for line in diff.splitlines()
            if line.startswith("+") and not line.startswith("+++")
        ]
        introduced = any(done_re.search(line) for line in added_lines)
    except Exception:
        pass

# Émettre le chemin uniquement si la transition est confirmée
if introduced:
    print(file_path)
PYEOF
)

# Rien détecté → exit silencieux (cas normal : édition sans transition DONE)
[[ -n "$DETECTED_FILE" ]] || exit 0

# ─── Lancer les commits pour toutes les Issues DONE non committées ────────────
ROOT=$(git -C "$(dirname "$DETECTED_FILE")" rev-parse --show-toplevel 2>/dev/null) || exit 0

COMMIT_SCRIPT="$ROOT/.claude/scripts/commit-done-issues.sh"
[[ -f "$COMMIT_SCRIPT" ]] || exit 0

bash "$COMMIT_SCRIPT" 2>&1 || true
