# Recommendations Tracking

> Fichier de suivi des recommandations non-bloquantes émises par le Reviewer.
> **Lu par le Developer** avant de considérer une Issue comme terminée.
> **Mis à jour par le Reviewer** au moment de l'APPROVED initial.
> **Vidé par le Developer** après application + re-review confirmée.

---

## Workflow

```
Reviewer: APPROVED avec recommandations
  → écrit les recommandations ici avec statut PENDING
  → n'ajoute PAS le commit (en attente de révision)

Developer: lit ce fichier, applique les recommandations
  → passe les recommandations en APPLIED
  → demande re-review (@reviewer rereview)

Reviewer: re-review confirme les corrections
  → passe en CONFIRMED
  → exécute le commit (git add + git commit)
```

---

## Recommandations en Attente

> Format : `[ISSUE-XXX] [date] [STATUT] Description`

_Aucune recommandation en attente._

---

## Historique

| Date | Issue | Recommandation | Statut |
|---|---|---|---|
| 2026-06-14 | ISSUE-027 | [CRAFT-07] Logging structuré SLF4J | CONFIRMED |
| 2026-06-14 | ISSUE-027 | [DOC] Javadoc @ConditionalOnProperty corrigé | CONFIRMED |
| 2026-06-14 | ISSUE-034 | [CRAFT-07] Logging structuré TransportAgentRegistration + HeartbeatScheduler | CONFIRMED |
| 2026-06-14 | ISSUE-034 | [CRAFT-08] Constante NO_EXECUTION | CONFIRMED |
| 2026-06-14 | ISSUE-034 | [TEST-06] CountDownLatch au lieu de Thread.sleep() | CONFIRMED |
