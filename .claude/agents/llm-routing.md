# LLM Routing — Agent → Modèle

> Table de routing définitive. Chaque agent est exécuté avec un LLM spécifique.
> La sélection se fait au lancement de la session, pas dynamiquement.
>
> Outil d'exécution : `.claude/scripts/agent.sh <agent>` ou `.claude/scripts/agent.py <agent>`

---

## Table de Routing

| Agent | LLM | Provider | Justification |
|---|---|---|---|
| **system-designer** | `claude-opus-4-5` | Anthropic API | Lecture de 14 specs + production de PDRs avec interfaces Java compilables — nécessite le meilleur niveau de compréhension structurelle |
| **architect** | `claude-opus-4-5` | Anthropic API | Décisions architecturales, ADRs, raisonnement sur les trade-offs — même exigence |
| **developer** | `deepseek-v4-pro` | DeepSeek API | Implémentation Java précise, bon rapport qualité/coût, fort sur le code |
| **reviewer** | `deepseek-v4-pro` | DeepSeek API | Vérification de checklists, lecture de code — tâche structurée, pas de raisonnement architectural |
| **tester** | `deepseek-v4-pro` | DeepSeek API | Écriture de tests Testcontainers — tâche technique structurée |

---

## Variables d'Environnement Requises

```bash
# Anthropic — System Designer + Architect
ANTHROPIC_API_KEY=sk-ant-...

# DeepSeek — Developer + Reviewer + Tester
DEEPSEEK_API_KEY=sk-...
```

---

## Endpoints

| Provider | Base URL | Modèle |
|---|---|---|
| Anthropic | native Claude Code auth | `opus` (alias résolu par Claude Code) |
| DeepSeek | `https://api.deepseek.com/anthropic` | `deepseek-v4-pro` |

> DeepSeek expose un endpoint compatible **Anthropic SDK** (pas OpenAI) via `ANTHROPIC_BASE_URL`.
> C'est pourquoi `agent.sh` set `ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic`.

---

## Frontmatter Claude Code — Valeurs valides pour `model:`

Le champ `model:` dans `.claude/agents/*.md` n'accepte que : `sonnet`, `opus`, `haiku`, `inherit`.
Les identifiants complets (`claude-opus-4-8`, `deepseek-v4-pro`) sont **invalides** dans ce champ.

| Agent | `model:` frontmatter | Modèle réel (via agent.sh) |
|---|---|---|
| system-designer | `opus` | Claude Opus (Anthropic natif) |
| architect | `opus` | Claude Opus (Anthropic natif) |
| developer | `inherit` | `deepseek-v4-pro` (via `ANTHROPIC_MODEL` env var) |
| reviewer | `inherit` | `deepseek-v4-pro` (via `ANTHROPIC_MODEL` env var) |
| tester | `inherit` | `deepseek-v4-pro` (via `ANTHROPIC_MODEL` env var) |

---

## Limites de Contexte

| LLM | Context window | Impact sur ce projet |
|---|---|---|
| claude-opus-4-5 | 200K tokens | Suffisant pour charger toutes les specs (System Designer) |
| deepseek-v4-pro | 64K tokens | Suffisant pour Developer (session-state + 1 Issue + 1 PDR ≈ 3-5K tokens) |

---

## Coût Estimé par Session

| Agent | Tokens typiques (in+out) | Coût estimé |
|---|---|---|
| system-designer (initial) | ~50K in + 20K out | ~$0.07 (Opus) |
| architect (escalade) | ~10K in + 3K out | ~$0.02 (Opus) |
| developer (par Issue) | ~5K in + 8K out | ~$0.004 (DeepSeek) |
| reviewer (par Issue) | ~6K in + 3K out | ~$0.003 (DeepSeek) |
| tester (par Issue) | ~5K in + 6K out | ~$0.003 (DeepSeek) |

> Projet complet estimé (~50 Issues) : < $1.50 total.
> Le coût dominant est le System Designer initial — une seule session.
