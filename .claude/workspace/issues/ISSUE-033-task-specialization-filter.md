# ISSUE-033 — TaskSpecializationFilter + TaskFilterResult

**PDR** : PDR-009
**Module** : `platform-agent-runtime`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-026, ISSUE-007
**Estime** : M

---

## Objectif

Créer le module `platform-agent-runtime` et implémenter `TaskSpecializationFilter` avec le
type scellé `TaskFilterResult`.

## Fichiers à Créer

```
platform-agent-runtime/pom.xml — dépend de domain, application, transport
platform-agent-runtime/src/main/java/com/performance/platform/agent/filter/
  ├── TaskSpecializationFilter.java
  ├── TaskFilterResult.java
  └── DefaultTaskSpecializationFilter.java

platform-agent-runtime/src/test/java/com/performance/platform/agent/filter/
  └── DefaultTaskSpecializationFilterTest.java — Responsible / NotResponsible
```

## Interfaces à Implémenter

```java
public interface TaskSpecializationFilter { TaskFilterResult filter(TaskExecutionRequest request); }

public sealed interface TaskFilterResult
        permits TaskFilterResult.Responsible, TaskFilterResult.NotResponsible {
    record Responsible(MessageId messageId, AgentId agentId) implements TaskFilterResult {}
    record NotResponsible(MessageId messageId, AgentId agentId) implements TaskFilterResult {}
}

@Component
public class DefaultTaskSpecializationFilter implements TaskSpecializationFilter {
    // construit avec supportedTaskNames + agentId
}
```

## Règles Spécifiques

- `Responsible` si `supportedTaskNames.contains(request.step().taskName())`, sinon `NotResponsible`.
- Pas de sélection — décision purement locale.

## Critères de Done

- [ ] `mvn test -pl platform-agent-runtime -q` → 0 erreur
- [ ] taskName supporté → `Responsible` ; non supporté → `NotResponsible`
- [ ] `.claude/progress.md` mis à jour : ISSUE-033 → DONE
- [ ] `.claude/context/interfaces-registry.md` : `TaskSpecializationFilter`, `TaskFilterResult` → STABLE
