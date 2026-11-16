# ISSUE-227 — GrantShare, signal de grant et contexte

**PDR** : PDR-060
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : —
**Estime** : M (1-3h)

---

## Objectif

Créer `GrantShare`, l'ajouter à `TaskGrantSignal` et l'exposer en lecture seule sur `ExecutionContext`.

> Référence : ADR-067.

## Fichiers à Créer / Modifier

```
core/platform-domain/src/main/java/com/performance/platform/domain/execution/
  ├── GrantShare.java               — (création)
  └── ExecutionContext.java         — share() + withShare() (modification)
core/platform-domain/src/main/java/com/performance/platform/domain/event/TaskGrantSignal.java     — + share (modification)
core/platform-domain/src/test/java/com/performance/platform/domain/execution/GrantShareTest.java, ExecutionContextShareTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-060` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Ajout de composant à un record public : conserver un constructeur secondaire avec l'ancienne signature (valeur par défaut documentée) pour que les autres modules compilent sans modification dans cette Issue.
- Invariants : `count >= 1`, `0 <= index < count`, `0 < fraction <= 1`.
- `ExecutionContext.with(...)` conserve le share ; défaut `GrantShare.single()`. Aucune mutation.
- Modification d'interfaces publiques couverte par ADR-067.

## Critères de Done

- [ ] `mvn test -pl :platform-domain -q` → 0 erreur
- [ ] Anciens constructeurs toujours utilisables
- [ ] `with()` ne perd pas le share
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-227 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
