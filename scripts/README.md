# Scripts — Agent Runner

## Ce que font ces scripts

Setter les variables d'environnement puis lancer `claude`. C'est tout.

Le system prompt de chaque agent est chargé depuis `.claude/agents/<agent>.md` —
source unique, utilisée à la fois par `agent.py/sh` et par Claude Code (subagents natifs).

> **Note importante** : les deux scripts exécutent `claude auth logout` avant chaque lancement
> d'agent, pour garantir un état propre : pas de session Anthropic Pro parasite quand on
> bascule vers DeepSeek, ni de variables DeepSeek résiduelles quand on repasse sur Anthropic.

| Agent | Auth step | Env settée | LLM lancé |
|---|---|---|---|
| `developer` | `claude auth logout` → déconnecte Anthropic Pro | `ANTHROPIC_BASE_URL` = DeepSeek + `ANTHROPIC_API_KEY` = `$DEEPSEEK_API_KEY` + `ANTHROPIC_MODEL` = `deepseek-v4-pro` | Claude Code → DeepSeek V4 Pro |
| `reviewer` | idem | idem | Claude Code → DeepSeek V4 Pro |
| `tester` | idem | idem | Claude Code → DeepSeek V4 Pro |
| `system-designer` | `claude auth logout` → déconnecte DeepSeek + `claude auth login` | `ANTHROPIC_BASE_URL`, `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL` **supprimées** | Claude Code → Anthropic (auth natif) |
| `architect` | idem | idem | Claude Code → Anthropic (auth natif) |

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
