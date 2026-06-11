# Scripts — Agent Runner

## Ce que font ces scripts

Setter les variables d'environnement puis lancer `claude`. C'est tout.

Le system prompt de chaque agent est chargé depuis `.claude/agents/<agent>.md` —
source unique, utilisée à la fois par `agent.py/sh` et par Claude Code (subagents natifs).

| Agent | Env settée | LLM lancé |
|---|---|---|
| `developer` | `ANTHROPIC_BASE_URL` = DeepSeek + `ANTHROPIC_API_KEY` = `$DEEPSEEK_API_KEY` | Claude Code → DeepSeek |
| `reviewer` | idem | Claude Code → DeepSeek |
| `tester` | idem | Claude Code → DeepSeek |
| `system-designer` | `ANTHROPIC_BASE_URL` supprimée, `ANTHROPIC_API_KEY` = `$ANTHROPIC_API_KEY` | Claude Code → Anthropic |
| `architect` | idem | Claude Code → Anthropic |

---

## Setup

```bash
cp .env.example .env
# Renseigner ANTHROPIC_API_KEY et DEEPSEEK_API_KEY
```

---

## Utilisation

```bash
# Python
python3 scripts/agent.py developer
python3 scripts/agent.py system-designer

# Bash
./scripts/agent.sh developer
./scripts/agent.sh architect
```

---

## Shell functions (optionnel)

Ajouter dans `~/.zshrc` / `~/.bashrc` pour taper directement `dev` ou `designer` :

```bash
source /path/to/performance-platform/scripts/shell-functions.sh
```

---

## Git Hooks

```bash
# Installer une seule fois après git init/clone
bash scripts/setup-hooks.sh
```

`prepare-commit-msg` : préfixe automatiquement le message de commit avec `[ISSUE-XXX]`
si une Issue est IN PROGRESS dans `progress.md`. Transparent — vous ne changez rien
à votre workflow git.
