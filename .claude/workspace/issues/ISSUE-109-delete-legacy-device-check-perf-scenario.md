# ISSUE-109 — Delete legacy device-check-perf.yaml scenario

**PDR** : PDR-025
**Module** : `platform-deployment`
**Statut** : DONE
**Priorite** : P0 (bloquant — scenario depend de code supprime)
**Bloquee par** : ISSUE-104 (DevicePopulationTaskExecutor + DeviceCheckTaskExecutor supprimes)
**Estime** : S (< 1h)

---

## Objectif

Supprimer le scenario legacy `device-check-perf.yaml` du repertoire `platform-deployment/docker/scenarios/`. Ce scenario depend de `DevicePopulationTaskExecutor` et `DeviceCheckTaskExecutor` qui sont supprimes par ISSUE-104. Le scenario est obsolete car il utilise `task: device-population` et `task: device-check` — deux tasks qui n'existent plus. De plus, il reference `wiremock` comme service standalone sur `http://wiremock:8080` (URL inline), ce qui viole l'architecture cible.

## Fichier a Supprimer

```
platform-deployment/docker/scenarios/
  └── device-check-perf.yaml          — DELETE
```

## Fichiers a Verifier et Mettre a Jour

```
platform-deployment/src/test/
  └── (tout test qui reference device-check-perf.yaml)
  └── (IotScenarioParseTest ou equivalent — s'il parse ce fichier)

platform-scenario-dsl/src/test/
  └── (tout test de parsing qui reference ce fichier)
```

## Regles Specifiques

- **Ne pas supprimer** le repertoire `platform-deployment/docker/scenarios/` — il peut contenir d'autres fichiers
- Si le repertoire devient vide apres suppression, le conserver (il sera reutilise pour de futurs scenarios)
- Verifier si des tests de parsing de scenario chargent `device-check-perf.yaml` — les mettre a jour pour ne plus le referencer

## Criteres de Done

- [ ] `device-check-perf.yaml` supprime
- [ ] Aucun test ne fait reference a `device-check-perf.yaml`
- [ ] `mvn test -pl platform-deployment -q` -> 0 erreur
- [ ] `mvn test -pl platform-scenario-dsl -q` -> 0 erreur
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-109 -> DONE
