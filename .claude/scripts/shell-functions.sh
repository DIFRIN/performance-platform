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
devloop()   { bash "$(_perf_root)/.claude/scripts/dev-loop.sh" "$@"; }

perf-status() {
  bash "$(_perf_root)/.claude/scripts/progress-status.sh"
}
