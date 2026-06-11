#!/usr/bin/env python3
"""
agent.py — Configure l'environnement et lance Claude Code avec le bon LLM.

Usage:
    python scripts/agent.py <agent>

Agents DeepSeek  : developer | reviewer | tester
Agents Anthropic : system-designer | architect

Ce script ne fait qu'une chose : setter les variables d'env et exec claude.
Le system prompt est chargé depuis .claude/agents/<agent>.md (source unique).
"""

import os
import sys
import subprocess
from pathlib import Path

# ─── Routing ──────────────────────────────────────────────────────────────────

DEEPSEEK_AGENTS   = {"developer", "reviewer", "tester"}
ANTHROPIC_AGENTS  = {"system-designer", "architect"}
ALL_AGENTS        = DEEPSEEK_AGENTS | ANTHROPIC_AGENTS

# ─── Helpers ──────────────────────────────────────────────────────────────────

def load_env(project_root: Path) -> None:
    env_file = project_root / ".env"
    if not env_file.exists():
        return
    for line in env_file.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        os.environ.setdefault(key.strip(), value.strip())


def find_project_root() -> Path:
    current = Path(__file__).resolve().parent
    for _ in range(6):
        if (current / "CLAUDE.md").exists():
            return current
        current = current.parent
    return Path.cwd()


# ─── Main ─────────────────────────────────────────────────────────────────────

def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        print("Usage: python scripts/agent.py <agent>")
        print("  DeepSeek  : developer | reviewer | tester")
        print("  Anthropic : system-designer | architect")
        sys.exit(0 if len(sys.argv) > 1 else 1)

    agent = sys.argv[1]
    if agent not in ALL_AGENTS:
        print(f"❌  Unknown agent: {agent}")
        print(f"   Valid: {' | '.join(sorted(ALL_AGENTS))}")
        sys.exit(1)

    project_root = Path(os.environ.get("PROJECT_ROOT", "")) or find_project_root()
    load_env(project_root)

    env = os.environ.copy()

    if agent in DEEPSEEK_AGENTS:
        api_key = env.get("DEEPSEEK_API_KEY", "")
        if not api_key:
            print("❌  DEEPSEEK_API_KEY not set in .env")
            sys.exit(1)
        env["ANTHROPIC_BASE_URL"] = "https://api.deepseek.com/anthropic"
        env["ANTHROPIC_API_KEY"]  = api_key
        env["ANTHROPIC_MODEL"]    = "deepseek-chat"
        env.pop("ANTHROPIC_BASE_URL_BACKUP", None)
        print(f"🟦 {agent} → DeepSeek ({env['ANTHROPIC_BASE_URL']})")

    else:  # ANTHROPIC_AGENTS
        api_key = env.get("ANTHROPIC_API_KEY", "")
        if not api_key:
            print("❌  ANTHROPIC_API_KEY not set in .env")
            sys.exit(1)
        env.pop("ANTHROPIC_BASE_URL", None)
        env.pop("ANTHROPIC_MODEL", None)
        print(f"🟧 {agent} → Anthropic (claude.ai)")

    prompt_file = project_root / ".claude" / "agents" / f"{agent}.md"
    if not prompt_file.exists():
        print(f"❌  Prompt file not found: {prompt_file}")
        sys.exit(1)

    system_prompt = prompt_file.read_text(encoding="utf-8")

    os.execvpe(
        "claude",
        ["claude", "--add-dir", str(project_root), "--system-prompt", system_prompt],
        env,
    )


if __name__ == "__main__":
    main()
