# ISSUE-218 — Santé du générateur et sorties du résultat

**PDR** : PDR-058
**Module** : `platform-injection-gatling`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-217, ISSUE-202
**Estime** : M (1-3h)

---

## Objectif

Le worker mesure CPU, GC et heap ; l'executor publie histogramme, fenêtres, instants et santé dans le `TaskResult`.

> Référence : ADR-068.

## Fichiers à Créer / Modifier

```
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/worker/
  ├── GeneratorHealthProbe.java     — MXBeans du worker (création)
  └── InjectionWorkerMain.java      — écrit la santé dans result.json (modification)
infra/platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/GatlingTaskExecutor.java — sorties : histogram, windows, startedAt, endedAt, generatorHealth, complete (modification)
infra/platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/worker/GeneratorHealthProbeTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-058` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- CPU = charge moyenne du processus (`OperatingSystemMXBean`) ; GC = somme des temps de collecte.
- Sorties sous forme de `Map`/`List` simples (via `toMap()` du domaine) pour traverser le transport.
- Déclarer `providesHistogram=true` et `providesWindows=true` dans les capacités.
- Simulation opaque : fenêtres sans `plannedArrivals`, `arrivalKnown=false`.

## Critères de Done

- [ ] `mvn test -pl :platform-injection-gatling -q` → 0 erreur
- [ ] Un run de test renvoie histogramme non vide, fenêtres et santé
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-218 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
