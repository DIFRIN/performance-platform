# ISSUE-239 — Validation du bloc requests

**PDR** : PDR-061
**Module** : `platform-scenario-dsl`
**Statut** : APPROVED
**Priorité** : P2
**Bloquée par** : ISSUE-207
**Estime** : S (< 1h)

---

## Objectif

Valider `parameters.requests` (mix pondéré) d'un step d'injection.

> Référence : ADR-070.

## Fichiers à Créer / Modifier

```
performance/platform-scenario-dsl/src/main/java/com/performance/platform/scenario/validation/RequestMixValidator.java     — (création) + appel depuis DefaultScenarioValidator
(+ tests)
```

## Interfaces à Implémenter

> Signatures dans `PDR-061` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Liste non vide ; `name` unique `[a-z0-9-]+` ; `weight` > 0.
- `requests` et la forme mono-requête (`path`/`method` au niveau du step) sont exclusifs.
- Chaque entrée respecte les règles de la requête unique existante.

## Critères de Done

- [ ] `mvn test -pl :platform-scenario-dsl -q` → 0 erreur
- [ ] Règles testées positivement et négativement
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-239 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
