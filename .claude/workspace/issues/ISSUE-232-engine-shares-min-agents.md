# ISSUE-232 — Moteur : calcul des parts et minAgents

**PDR** : PDR-060
**Module** : `platform-execution-engine`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-227, ISSUE-228
**Estime** : M (1-3h)

---

## Objectif

À la clôture de la fenêtre de claim, le dispatcher vérifie `minAgents` puis émet les grants avec leur part.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
execution/platform-execution-engine/src/main/java/com/performance/platform/engine/remote/
  ├── TransportStepDispatcher.java  — parts pour fanOut all, contrôle minAgents (modification)
  └── TransportExecutionEventHandler.java — share single pour fanOut one (modification)
(+ tests du dispatcher étendus)
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Agents triés par `AgentId` ; `GrantShare.equal(i, n)`.
- `granted.size() < minAgents` : aucun grant accordé, claims rejetés, étape en échec explicite (`min_agents_not_met granted={} required={}`).
- Le moteur ne lit jamais `workload` ni aucun paramètre de charge (ADR-045).

## Critères de Done

- [ ] `mvn test -pl :platform-execution-engine -q` → 0 erreur
- [ ] 3 claims => parts (0,3),(1,3),(2,3)
- [ ] 2 claims avec minAgents=3 => aucun grant, étape FAILED
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-232 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
