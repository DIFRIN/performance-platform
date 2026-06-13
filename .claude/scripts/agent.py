#!/usr/bin/env python3
"""
agent.py — Configure l'environnement et lance Claude Code avec le bon LLM.

Usage:
    python scripts/agent.py <agent>

Agents DeepSeek  : developer | reviewer | tester
  → logout from Anthropic Pro account first
  → sets ANTHROPIC_BASE_URL + ANTHROPIC_API_KEY from .env DEEPSEEK_API_KEY

Agents Anthropic : system-designer | architect
  → unsets ANTHROPIC_BASE_URL and ANTHROPIC_MODEL so Claude Code uses its own auth
  → no ANTHROPIC_API_KEY required in .env

Ce script ne fait qu'une chose : setter les variables d'env et exec claude.
Le system prompt est chargé depuis .claude/agents/<agent>.md (source unique).
"""

import os
import subprocess
import sys
from pathlib import Path

# ─── Routing ──────────────────────────────────────────────────────────────────

DEEPSEEK_AGENTS  = {"developer", "reviewer", "tester"}
ANTHROPIC_AGENTS = {"system-designer", "architect"}
ALL_AGENTS       = DEEPSEEK_AGENTS | ANTHROPIC_AGENTS

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



def check_anthropic_auth() -> bool:
    """
    Returns True if Claude Code is authenticated (claude auth status exit code 0).
    Returns False if not logged in (exit code 1).
    """
    result = subprocess.run(
        ["claude", "auth", "status"],
        capture_output=True,
        text=True
    )
    return result.returncode == 0


def ensure_anthropic_logged_out() -> None:
    """
    If logged into Anthropic Pro, logs out first.
    This avoids auth conflicts when switching to DeepSeek or re-authenticating.
    """
    if not check_anthropic_auth():
        return

    print("🚪 Logged into Anthropic Pro — logging out before switching to DeepSeek...")
    result = subprocess.run(
        ["claude", "auth", "logout"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        print(f"❌  Logout failed: {result.stderr.strip()}")
        print("   Run 'claude auth logout' manually.")
        sys.exit(1)

    # Verify logout succeeded
    if check_anthropic_auth():
        print("❌  Still authenticated after logout attempt.")
        print("   Run 'claude auth logout' manually.")
        sys.exit(1)

    print("✅  Logged out from Anthropic Pro.")
    print()


def ensure_anthropic_auth() -> None:
    """
    Checks Claude Code auth. If not logged in, runs claude auth login
    and waits for the user to complete the OAuth flow before continuing.
    """
    if check_anthropic_auth():
        return

    print("🔐 Not logged in to Claude Code.")
    print("   Starting authentication — your browser will open.")
    print("   Complete the login in the browser, then return here.")
    print()

    try:
        subprocess.run(["claude", "auth", "login"], check=True)
    except subprocess.CalledProcessError:
        print("❌  Authentication failed or was cancelled.")
        print("   Run 'claude auth login' manually and retry.")
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n   Cancelled.")
        sys.exit(1)

    # Verify auth succeeded after login
    if not check_anthropic_auth():
        print("❌  Still not authenticated after login attempt.")
        print("   Run 'claude auth status' to diagnose.")
        sys.exit(1)

    print("✅  Authenticated successfully.")
    print()


# ─── Main ─────────────────────────────────────────────────────────────────────

def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        print("Usage: python scripts/agent.py <agent>")
        print("  DeepSeek  : developer | reviewer | tester  (requires DEEPSEEK_API_KEY in .env)")
        print("  Anthropic : system-designer | architect    (uses Claude Code native auth)")
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
        ensure_anthropic_logged_out()
        api_key = env.get("DEEPSEEK_API_KEY", "")
        if not api_key:
            print("❌  DEEPSEEK_API_KEY not set in .env")
            sys.exit(1)
        env["ANTHROPIC_BASE_URL"] = "https://api.deepseek.com/anthropic"
        env["ANTHROPIC_API_KEY"]  = api_key
        env["ANTHROPIC_MODEL"]    = "deepseek-v4-pro"
        print(f"🟦 {agent} → DeepSeek {env['ANTHROPIC_MODEL']} ({env['ANTHROPIC_BASE_URL']})")

    else:  # ANTHROPIC_AGENTS — let Claude Code use its own auth
        ensure_anthropic_logged_out()
        env.pop("ANTHROPIC_BASE_URL", None)
        env.pop("ANTHROPIC_API_KEY",  None)
        env.pop("ANTHROPIC_MODEL",    None)
        ensure_anthropic_auth()
        print(f"🟧 {agent} → Anthropic (Claude Code native auth)")

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
