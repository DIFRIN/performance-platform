# ISSUE-177 — Cut execution-engine → agent-runtime Edge

**PDR** : PDR-048
**Module** : `platform-execution-engine`, `platform-bootstrap`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : —
**Estime** : M (1-3h)

---

## Objectif

Supprimer l'arête `execution-engine → agent-runtime` (A3) : `LocalLifecycleDispatcher`
importe `LocalAgent`. Solution retenue : un port étroit
`application.ports.out.LifecycleSignalHandler`, implémenté par `LocalAgent`.
Le dispatcher reste dans l'engine et ne dépend plus de l'agent-runtime.

## Fichiers à Modifier

```
core/platform-use-cases/src/main/java/com/performance/platform/application/ports/out/
  └── LifecycleSignalHandler.java          — NOUVEAU (port, 0 annotation)

execution/platform-agent-runtime/src/main/java/com/performance/platform/agent/local/
  └── LocalAgent.java                      — implements LifecycleSignalHandler

execution/platform-execution-engine/src/main/java/com/performance/platform/engine/lifecycle/
  └── LocalLifecycleDispatcher.java        — dépend du port, plus de LocalAgent

bootstrap/src/main/java/com/performance/platform/app/config/
  └── AgentRuntimeConfiguration.java       — le @Bean LOCAL retourne LocalAgent (type concret)

execution/platform-execution-engine/pom.xml          — retirer platform-agent-runtime
```

## Règles Spécifiques

- `platform-execution-engine` ne doit plus importer aucun type de `platform-agent-runtime`.
- Le bean `ExecutionLifecycleDispatcher` reste unique par mode (LOCAL/DISTRIBUTED).
- Aucun changement de comportement (pur déplacement).

## Critères de Done

- [ ] `LocalLifecycleDispatcher` vit dans `platform-bootstrap`
- [ ] `platform-execution-engine` n'importe plus `platform-agent-runtime`
- [ ] `mvn test -pl :platform-execution-engine,:platform-bootstrap -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-177 → DONE
