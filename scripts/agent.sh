#!/usr/bin/env bash
# =============================================================================
# agent.sh — Configure l'environnement et lance Claude Code avec le bon LLM.
#
# Usage :
#   ./scripts/agent.sh <agent>
#
# Agents DeepSeek  : developer | reviewer | tester
#   → logout from Anthropic Pro account first
#   → sets ANTHROPIC_BASE_URL + ANTHROPIC_API_KEY from .env DEEPSEEK_API_KEY
#
# Agents Anthropic : system-designer | architect
#   → unsets ANTHROPIC_BASE_URL and ANTHROPIC_MODEL so Claude Code uses its own auth
#   → no ANTHROPIC_API_KEY required in .env
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(dirname "$SCRIPT_DIR")}"
ENV_FILE="$PROJECT_ROOT/.env"

# Charger .env
if [[ -f "$ENV_FILE" ]]; then
  set -a; source "$ENV_FILE"; set +a
fi

AGENT="${1:-}"

if [[ -z "$AGENT" ]]; then
  echo "Usage: $0 <agent>"
  echo "  DeepSeek  : developer | reviewer | tester  (requires DEEPSEEK_API_KEY in .env)"
  echo "  Anthropic : system-designer | architect    (uses Claude Code native auth)"
  exit 1
fi


# ─── Auth helpers ────────────────────────────────────────────────────────────

ensure_anthropic_logged_out() {
  # claude auth status: exit 0 = logged in, exit 1 = not logged in
  if claude auth status &>/dev/null; then
    echo "🚪 Logged into Anthropic Pro — logging out before switching to DeepSeek..."
    claude auth logout
    # Verify logout succeeded
    if claude auth status &>/dev/null; then
      echo "❌  Logout failed. Run 'claude auth logout' manually."
      exit 1
    fi
    echo "✅  Logged out from Anthropic Pro."
    echo ""
  fi
}

ensure_anthropic_auth() {
  if claude auth status &>/dev/null; then
    return 0
  fi

  echo "🔐 Not logged in to Claude Code."
  echo "   Starting authentication — your browser will open."
  echo "   Complete the login in the browser, then return here."
  echo ""

  if ! claude auth login; then
    echo "❌ Authentication failed or was cancelled."
    echo "   Run 'claude auth login' manually and retry."
    exit 1
  fi

  # Verify auth succeeded
  if ! claude auth status &>/dev/null; then
    echo "❌ Still not authenticated after login attempt."
    echo "   Run 'claude auth status' to diagnose."
    exit 1
  fi

  echo "✅ Authenticated successfully."
  echo ""
}

case "$AGENT" in

  developer|reviewer|tester)
    ensure_anthropic_logged_out
    if [[ -z "${DEEPSEEK_API_KEY:-}" ]]; then
      echo "❌ DEEPSEEK_API_KEY not set in .env"; exit 1
    fi
    export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"
    export ANTHROPIC_API_KEY="$DEEPSEEK_API_KEY"
    export ANTHROPIC_MODEL="deepseek-v4-pro"
    echo "🟦 $AGENT → DeepSeek ${ANTHROPIC_MODEL} (${ANTHROPIC_BASE_URL})"
    ;;

  system-designer|architect)
    ensure_anthropic_logged_out
    ensure_anthropic_auth
    echo "🟧 $AGENT → Anthropic (Claude Code native auth)"
    ;;

  *)
    echo "❌ Unknown agent: $AGENT"
    echo "   Valid: developer | reviewer | tester | system-designer | architect"
    exit 1
    ;;
esac

PROMPT_FILE="$PROJECT_ROOT/.claude/agents/${AGENT}.md"
if [[ ! -f "$PROMPT_FILE" ]]; then
  echo "❌ Prompt file not found: $PROMPT_FILE"; exit 1
fi

exec claude \
  --add-dir "$PROJECT_ROOT" \
  --system-prompt "$(cat "$PROMPT_FILE")"
