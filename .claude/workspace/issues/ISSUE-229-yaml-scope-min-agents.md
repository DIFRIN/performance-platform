# ISSUE-229 — Parsing YAML : scope, minAgents, onAgentLoss

**PDR** : PDR-060
**Module** : `platform-scenario-yaml`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-228, ISSUE-206
**Estime** : S (< 1h)

---

## Objectif

Lire `minAgents` et `onAgentLoss` au niveau du step ; `workload.scope` est déjà porté par le plan.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
infra/platform-scenario-yaml/src/main/java/com/performance/platform/scenario/parser/dto/StepYamlDto.java          — + minAgents, onAgentLoss (modification)
infra/platform-scenario-yaml/src/main/java/com/performance/platform/scenario/parser/YamlScenarioParser.java        — mapping (modification)
(+ test du parseur étendu)
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Valeurs YAML : `onAgentLoss: abort | continue`, `scope: per-agent | global`.
- Valeur inconnue => erreur de parsing avec l'id du step.

## Critères de Done

- [ ] `mvn test -pl :platform-scenario-yaml -q` → 0 erreur
- [ ] Step sans ces clés => défauts
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-229 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
