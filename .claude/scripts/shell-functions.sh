# =============================================================================
# shell-functions.sh — Wrappers sur agent.py
#
# Ajouter dans ~/.zshrc ou ~/.bashrc :
#   export ANTHROPIC_API_KEY="sk-ant-..."
#   export DEEPSEEK_API_KEY="sk-..."
#   source /path/to/performance-platform/scripts/shell-functions.sh
# =============================================================================

_perf_root() { echo "${PERF_PROJECT_ROOT:-$PWD}"; }

designer()  { python3 "$(_perf_root)/scripts/agent.py" system-designer "$@"; }
architect() { python3 "$(_perf_root)/scripts/agent.py" architect        "$@"; }
dev()       { python3 "$(_perf_root)/scripts/agent.py" developer        "$@"; }
reviewer()  { python3 "$(_perf_root)/scripts/agent.py" reviewer         "$@"; }
tester()    { python3 "$(_perf_root)/scripts/agent.py" tester           "$@"; }

perf-status() {
  local pf="$(_perf_root)/progress.md"
  echo "═══ Performance Platform ═══"
  echo "IN PROGRESS : $(grep -c 'IN PROGRESS' "$pf" 2>/dev/null || echo 0)"
  echo "IN REVIEW   : $(grep -c 'IN REVIEW'   "$pf" 2>/dev/null || echo 0)"
  echo "DONE        : $(grep -c 'DONE'         "$pf" 2>/dev/null || echo 0)"
  echo "WAITING     : $(grep -c 'WAITING'      "$pf" 2>/dev/null || echo 0)"
}
