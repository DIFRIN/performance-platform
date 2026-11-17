# ISSUE-244 — Documentation, exemples et acceptation des ADR

**PDR** : PDR-062
**Module** : `docs`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : ISSUE-203, ISSUE-210, ISSUE-211, ISSUE-214, ISSUE-220, ISSUE-225, ISSUE-226, ISSUE-237, ISSUE-243, ISSUE-209, ISSUE-239, ISSUE-241, ISSUE-229, ISSUE-230, ISSUE-222
**Estime** : M (1-3h)

---

## Objectif

Mettre à jour la documentation de référence et les exemples, puis passer ADR-064..070 à ACCEPTED.

> Référence : ADR-064..070, ADR-061.

## Fichiers à Créer / Modifier

```
docs/knowledge/architecture.md                — état courant (worker, plan, share, mesures, verdict)
docs/knowledge/specs/01-scenario-dsl.md       — workload, requests, integrity, minAgents, onAgentLoss
docs/knowledge/specs/04-agent-runtime.md      — refus de claim, share
docs/knowledge/specs/05-transport-layer.md    — TaskGrantSignal.share
docs/knowledge/specs/06-injection-gatling.md  — worker, traducteur, capacités, mix
docs/knowledge/specs/07-assertion-framework.md — MERGE, métriques, famille integrity
docs/knowledge/specs/08-report-engine.md      — verdict INVALID, preuves
docs/knowledge/glossary.md                    — WorkloadPlan, Stage, GrantShare, INVALID
docs/knowledge/adr/ADR-064..070               — Status → ACCEPTED (date)
distribution/example/scenarios/*.yaml         — un exemple `workload` + `scope: global`
distribution/example/MODOP.md                 — variables INJECTION_*
```

## Interfaces à Implémenter

> Signatures dans `PDR-062` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Décision Architect requise pour l'acceptation et pour toute modification de `CLAUDE.md` (table de routing §8, signaux d'alarme §10 : `LoadModelType`, `LoadModel`).
- `bash .claude/scripts/check-adr-hygiene.sh` → OK.
- Aucune modification de code Java.

## Critères de Done

- [ ] Relecture croisée avec le code livré
- [ ] `architecture.md` décrit l'état livré
- [ ] ADR-064..070 en ACCEPTED
- [ ] Exemples exécutables en LOCAL et DISTRIBUTED
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-244 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
