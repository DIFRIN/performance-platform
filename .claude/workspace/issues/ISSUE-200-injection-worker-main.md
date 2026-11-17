# ISSUE-200 — InjectionWorkerMain et run directory

**PDR** : PDR-055
**Module** : `platform-injection-gatling`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-199
**Estime** : L (3-6h)

---

## Objectif

Créer le point d'entrée de la JVM fille : lit le run directory, lance Gatling, écrit le fichier résultat et sort avec un code explicite.

> Référence : ADR-066.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/worker/
  ├── InjectionWorkerMain.java      — main(args[0]=runDirectory)
  ├── WorkerRunSpec.java            — record d'entrée
  ├── RunDirectory.java             — lecture/écriture : spec.json, plan, protocole, ressources ; result.json
  └── WorkerOutcome.java            — record de sortie
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/worker/
  ├── RunDirectoryTest.java         — aller-retour écriture/lecture
  └── InjectionWorkerMainTest.java  — run directory invalide => code != 0
```

## Interfaces à Implémenter

> Signatures dans `PDR-055` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- `DefaultGatlingRunner` et `SimulationInjectionHolder` ne sont plus appelés que depuis le worker.
- Sérialisation avec Jackson déjà présent dans le module ; aucune nouvelle dépendance.
- Aucun secret écrit dans le run directory : lus depuis l'environnement.
- Codes : 0 terminé, 1 échec simulation, 2 run directory invalide.
- À ce stade le profil est encore le `loadModel` existant ; le plan arrive avec le PDR Workload Plan.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] `InjectionWorkerMain` exécutable seul sur un run directory de test
- [ ] `result.json` écrit en fin de run
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-200 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
