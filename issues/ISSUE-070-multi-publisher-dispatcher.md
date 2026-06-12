# ISSUE-070 — MultiPublisherDispatcher (adapter ReportPublisherPort)

**PDR** : PDR-016
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-065, ISSUE-013
**Estime** : M

---

## Objectif

Implémenter `MultiPublisherDispatcher` (package `.publisher`) : route un rapport vers tous les
`ReportPublisher` configurés. Adapter du port `ReportPublisherPort`.

## Fichiers à Créer

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/publisher/
  ├── MultiPublisherDispatcher.java
  └── PublishersProperties.java

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/publisher/
  └── MultiPublisherDispatcherTest.java — dispatch à N publishers, échec d'un seul n'arrête pas les autres
```

## Interfaces à Implémenter

```java
@Component
public class MultiPublisherDispatcher implements ReportPublisherPort {
    public MultiPublisherDispatcher(List<ReportPublisher> publishers, ReportEngine engine,
                                    PublishersProperties props) { /* ... */ }
    public void publish(ReportId reportId, ExecutionId executionId) { /* ... */ }
}
```

## Règles Spécifiques

- Tous les publishers configurés reçoivent le rapport.
- Échec d'un publisher → loggé, les autres continuent ; publie `ReportPublished` par succès.
- I/O sous Virtual Threads.
- Tout le code dans `..infrastructure.publisher..` (séparation stricte).

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur
- [ ] Dispatch à 3 publishers ; 1 échoue → 2 réussissent
- [ ] `progress.md` mis à jour : ISSUE-070 → DONE
- [ ] `context/interfaces-registry.md` : `MultiPublisherDispatcher` → STABLE
