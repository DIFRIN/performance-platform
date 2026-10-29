# ISSUE-170 — LocalDistributedEquivalenceTest (T2)

**PDR** : PDR-043
**Module** : `platform-bootstrap`
**Statut** : DONE
**Priorité** : P0 (test le plus précieux du repo — gate de la fusion A1)
**Bloquée par** : —
**Estime** : L (3-6h)

---

## Objectif

Écrire le test d'équivalence LOCAL≡DISTRIBUTED qui exécute le MÊME scénario à
travers les deux transports (in-memory) et compare `Verdict` + séquence de statuts
de tâches. Il DOIT passer contre le comportement actuel (aucune modif de prod).
C'est le filet qui rend la fusion A1 sûre (T2).

## Fichiers à Créer

```
bootstrap/src/test/java/com/performance/platform/app/e2e/
  └── LocalDistributedEquivalenceTest.java
  └── equivalence-scenario.yaml   — scénario minimal (prep + injection + assertion)
```

## Règles Spécifiques

- `@ParameterizedTest` sur { LOCAL, DISTRIBUTED } (DISTRIBUTED = RemoteExecutionEngine
  + InMemoryExecutionTransport + LocalAgent, tous in-memory, pas de Testcontainers).
- Assertions : `Verdict` identique, `ExecutionStatus` identique, séquence ordonnée
  `taskId → TaskStatus` identique.
- Test `*Test` (surefire), rapide. Doit être vert aujourd'hui (baseline).

## Critères de Done

- [ ] `LocalDistributedEquivalenceTest` vert sur le code actuel
- [ ] Le test exécute le même scénario en LOCAL et DISTRIBUTED
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-170 → DONE
