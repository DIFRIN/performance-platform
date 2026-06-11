#!/usr/bin/env bash
# =============================================================================
# agent.sh — Configure l'environnement et lance Claude Code avec le bon LLM.
#
# Usage :
#   ./scripts/agent.sh <agent>
#
# Agents DeepSeek  : developer | reviewer | tester
# Agents Anthropic : system-designer | architect
#
# Ce script ne fait qu'une chose : setter les variables d'env et lancer claude.
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
  echo "  DeepSeek  : developer | reviewer | tester"
  echo "  Anthropic : system-designer | architect"
  exit 1
fi

case "$AGENT" in

  developer|reviewer|tester)
    if [[ -z "${DEEPSEEK_API_KEY:-}" ]]; then
      echo "❌ DEEPSEEK_API_KEY not set in .env"; exit 1
    fi
    export ANTHROPIC_BASE_URL="https://api.deepseek.com/anthropic"
    export ANTHROPIC_API_KEY="$DEEPSEEK_API_KEY"
    export ANTHROPIC_MODEL="deepseek-chat"
    echo "🟦 $AGENT → DeepSeek (${ANTHROPIC_BASE_URL})"
    ;;

  system-designer|architect)
    if [[ -z "${ANTHROPIC_API_KEY:-}" ]]; then
      echo "❌ ANTHROPIC_API_KEY not set in .env"; exit 1
    fi
    unset ANTHROPIC_BASE_URL
    unset ANTHROPIC_MODEL
    echo "🟧 $AGENT → Anthropic (claude.ai)"
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
