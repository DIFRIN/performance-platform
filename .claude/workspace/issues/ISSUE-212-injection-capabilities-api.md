# ISSUE-212 — InjectionCapabilities dans plugin-api

**PDR** : PDR-057
**Module** : `platform-plugin-api`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : ISSUE-204
**Estime** : S (< 1h)

---

## Objectif

Ajouter le record `InjectionCapabilities` et l'interface optionnelle `InjectionCapabilityProvider`.

> Référence : ADR-065.

## Fichiers à Créer / Modifier

```
core/platform-plugin-api/src/main/java/com/performance/platform/plugin/
  ├── InjectionCapabilities.java
  └── InjectionCapabilityProvider.java
core/platform-plugin-api/src/test/java/com/performance/platform/plugin/
  └── InjectionCapabilitiesTest.java
```

## Interfaces à Implémenter

> Signatures dans `PDR-057` section « Interfaces Publiques » — les appliquer telles quelles.

## Règles Spécifiques

- Ensembles copiés défensivement (`Set.copyOf`).
- `TaskExecutor` non modifié ; 0 annotation Spring.

## Critères de Done

- [ ] `mvn test -pl :platform-plugin-api -q` → 0 erreur
- [ ] Record immuable testé
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-212 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
