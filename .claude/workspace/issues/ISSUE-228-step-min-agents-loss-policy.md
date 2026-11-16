# ISSUE-228 — StepDefinition : minAgents et onAgentLoss

**PDR** : PDR-060
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : —
**Estime** : S (< 1h)

---

## Objectif

Ajouter `AgentLossPolicy` et les composants `minAgents`, `onAgentLoss` à `StepDefinition`.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
core/platform-domain/src/main/java/com/performance/platform/domain/scenario/
  ├── AgentLossPolicy.java          — (création)
  └── StepDefinition.java           — + minAgents, onAgentLoss (modification)
core/platform-domain/src/test/java/com/performance/platform/domain/scenario/StepDefinitionTest.java — étendre
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Ajout de composant à un record public : conserver un constructeur secondaire avec l'ancienne signature (valeur par défaut documentée) pour que les autres modules compilent sans modification dans cette Issue.
- Défauts : `minAgents = 1`, `onAgentLoss = ABORT`. `minAgents < 1` => `IllegalArgumentException`.

## Critères de Done

- [ ] `mvn test -pl :platform-domain -q` → 0 erreur
- [ ] Ancien constructeur toujours utilisable
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-228 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
