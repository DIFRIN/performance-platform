# ISSUE-002 — Enums du domaine

**PDR** : PDR-001
**Module** : `platform-domain`
**Statut** : WAITING
**Priorité** : P0
**Bloquée par** : —
**Estime** : M

---

## Objectif

Créer tous les enums du domaine, y compris `AssertionOperator` avec sa méthode `evaluate()`.
`TransportType` n'a PAS de valeur GRPC. `TaskType` n'existe PAS.

## Fichiers à Créer

```
platform-domain/src/main/java/com/performance/platform/domain/
  ├── scenario/Phase.java
  ├── scenario/ExecutionMode.java
  ├── task/TaskStatus.java
  ├── execution/PhaseStatus.java
  ├── execution/ExecutionStatus.java
  ├── agent/AgentState.java
  ├── execution/TaskCompletionPolicy.java
  ├── injection/LoadModelType.java
  ├── assertion/AssertionOperator.java
  ├── assertion/AssertionStatus.java
  ├── report/Verdict.java
  ├── report/PublicationTarget.java
  ├── report/ReportFormat.java
  └── transport/TransportType.java

platform-domain/src/test/java/com/performance/platform/domain/assertion/
  └── AssertionOperatorTest.java — les 6 opérateurs sur des cas limites
```

## Interfaces à Implémenter

```java
public enum Phase { PREPARATION, INJECTION, ASSERTION }
public enum ExecutionMode { LOCAL, DISTRIBUTED }
public enum TaskStatus { SUCCESS, FAILED, SKIPPED, TIMEOUT }
public enum PhaseStatus { PENDING, RUNNING, COMPLETED, FAILED }
public enum ExecutionStatus { STARTED, RUNNING, COMPLETED, FAILED, CANCELLED }
public enum AgentState { REGISTERING, IDLE, EXECUTING, DRAINING, OFFLINE }
public enum TaskCompletionPolicy { FIRST_COMPLETE, ALL_COMPLETE }
public enum LoadModelType { RAMP, RAMP_UP_DOWN, CONSTANT, SPIKE, STAIR, SOAK, BURST, CUSTOM }
public enum AssertionStatus { PASSED, FAILED, SKIPPED, ERROR }
public enum Verdict { SUCCESS, WARNING, FAILED }
public enum PublicationTarget { CONFLUENCE, S3, SHAREPOINT, GIT, NEXUS, CUSTOM }
public enum ReportFormat { HTML, PDF, JSON }

// GRPC volontairement absent.
public enum TransportType { SOCKET, RABBITMQ, KAFKA, HTTP, IN_MEMORY, CUSTOM }

public enum AssertionOperator {
    LT, LTE, GT, GTE, EQ, NEQ;
    public boolean evaluate(double actual, double expected) {
        return switch (this) {
            case LT  -> actual < expected;
            case LTE -> actual <= expected;
            case GT  -> actual > expected;
            case GTE -> actual >= expected;
            case EQ  -> actual == expected;
            case NEQ -> actual != expected;
        };
    }
}
```

## Règles Spécifiques

- 0 annotation framework.
- NE PAS ajouter de valeur GRPC à `TransportType`.
- NE PAS créer d'enum `TaskType`.

## Critères de Done

- [ ] `mvn test -pl platform-domain -q` → 0 erreur
- [ ] `AssertionOperator.LT.evaluate(1, 2)` == true ; `.GT.evaluate(1, 2)` == false
- [ ] `TransportType` ne contient pas `GRPC`
- [ ] Aucun enum `TaskType` dans le module
- [ ] `progress.md` mis à jour : ISSUE-002 → DONE
- [ ] `context/interfaces-registry.md` : enums → STABLE
