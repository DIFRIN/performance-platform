# ISSUE-197 — Interface ExecutorAvailability

**PDR** : PDR-055
**Module** : `platform-plugin-api`
**Statut** : DONE
**Priorité** : P0
**Bloquée par** : —
**Estime** : S (< 1h)

---

## Objectif

Ajouter l'interface optionnelle `ExecutorAvailability` permettant à un executor de refuser temporairement de nouvelles tâches.

> Référence : ADR-066.

## Fichiers à Créer / Modifier

```
core/platform-plugin-api/src/main/java/com/performance/platform/plugin/
  └── ExecutorAvailability.java     — interface optionnelle (création)
core/platform-plugin-api/src/test/java/com/performance/platform/plugin/
  └── ExecutorAvailabilityTest.java — contrat + test ArchUnit : 0 annotation Spring
```

## Interfaces à Implémenter

```java
public interface ExecutorAvailability {
    boolean canAcceptNewTask();
}
```

## Règles Spécifiques

- `TaskExecutor` n'est PAS modifié.
- Aucune annotation Spring/JPA/Jackson dans `platform-plugin-api`.

## Critères de Done

- [ ] `mvn test -pl :platform-plugin-api -q` → 0 erreur
- [ ] Interface présente, Javadoc du contrat
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-197 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour si interfaces concernées
