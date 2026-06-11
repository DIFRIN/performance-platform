# LLM Routing — Agent → Modèle

> Table de routing définitive. Chaque agent est exécuté avec un LLM spécifique.
> La sélection se fait au lancement de la session, pas dynamiquement.
>
> Outil d'exécution : `scripts/agent.sh <agent>` ou `scripts/agent.py <agent>`

---

## Table de Routing

| Agent | LLM | Provider | Justification |
|---|---|---|---|
| **system-designer** | `claude-opus-4-5` | Anthropic API | Lecture de 14 specs + production de PDRs avec interfaces Java compilables — nécessite le meilleur niveau de compréhension structurelle |
| **architect** | `claude-opus-4-5` | Anthropic API | Décisions architecturales, ADRs, raisonnement sur les trade-offs — même exigence |
| **developer** | `deepseek-v3-0324` | DeepSeek API | Implémentation Java précise, bon rapport qualité/coût, fort sur le code |
| **reviewer** | `deepseek-v3-0324` | DeepSeek API | Vérification de checklists, lecture de code — tâche structurée, pas de raisonnement architectural |
| **tester** | `deepseek-v3-0324` | DeepSeek API | Écriture de tests Testcontainers — tâche technique structurée |

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
| Anthropic | `https://api.anthropic.com/v1/messages` | `claude-opus-4-5` |
| DeepSeek | `https://api.deepseek.com/v1/chat/completions` | `deepseek-chat` (alias de deepseek-v3-0324) |

DeepSeek expose une API compatible OpenAI — même format de requête que `/v1/chat/completions`.

---

## Limites de Contexte

| LLM | Context window | Impact sur ce projet |
|---|---|---|
| claude-opus-4-5 | 200K tokens | Suffisant pour charger toutes les specs (System Designer) |
| deepseek-v3-0324 | 64K tokens | Suffisant pour Developer (session-state + 1 Issue + 1 PDR ≈ 3-5K tokens) |

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
