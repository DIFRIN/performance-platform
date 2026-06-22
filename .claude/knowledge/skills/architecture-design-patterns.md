# Skill — Architecture Design Patterns

> Patterns d'architecture à appliquer dans ce projet.
> Lu par l'Architect pour valider les décisions structurelles,
> par le System Designer pour concevoir les PDRs avec les bonnes abstractions.
> Référence : ADR-004 (Hexagonal), ADR-007 (Plugin), ADR-008 (Spécialisation).

---

## 1. Ports & Adapters (Hexagonal Architecture)

Le domaine est au centre. Toute dépendance externe passe par un port.

```
External World          Application Core           External World
─────────────          ────────────────           ─────────────
HTTP Request  ──▶  [Driving Port]  ──▶  Domain  ──▶  [Driven Port]  ──▶  PostgreSQL
CLI           ──▶  UseCase iface         Logic   ──▶  Repository iface  ──▶  Kafka
Tests         ──▶                                 ──▶                   ──▶  S3
```

**Règles impératives pour ce projet :**

```java
// Driving port (in/) — l'extérieur appelle le domaine
package com.performance.platform.application.port.in;
public interface ExecuteScenarioUseCase {
    ExecutionId execute(ExecuteScenarioCommand command);
}

// Driven port (out/) — le domaine appelle l'extérieur
package com.performance.platform.application.port.out;
public interface ExecutionRepository {
    void saveTaskResult(ExecutionId id, TaskId taskId, AgentId agentId, TaskResult result);
}

// Adapter (infrastructure/) — implémente le port driven
package com.performance.platform.infrastructure.adapter.out;
@Repository
public class JpaExecutionRepository implements ExecutionRepository { ... }
```

**Signal d'alarme :** si une classe dans `domain/` ou `application/` importe
`org.springframework`, `jakarta.persistence`, ou `com.fasterxml.jackson` → violation.

---

## 2. Anti-Corruption Layer (ACL)

Isoler le domaine des modèles externes. Jamais d'entité JPA dans le domaine.

```java
// ✅ CORRECT — mapping explicite entre modèles
@Component
public class ExecutionStateMapper {
    // JPA Entity → Domain Record
    public ExecutionState toDomain(ExecutionStateEntity entity) {
        return new ExecutionState(
            ExecutionId.of(entity.getId()),
            ScenarioId.of(entity.getScenarioId()),
            ExecutionStatus.valueOf(entity.getStatus())
        );
    }
    // Domain Record → JPA Entity
    public ExecutionStateEntity toEntity(ExecutionState domain) { ... }
}

// ❌ INCORRECT — entité JPA annotée dans le domaine
@Entity // ← dans platform-domain → violation ADR-004
public class ExecutionState { ... }
```

---

## 3. CQRS — Séparation Commande / Requête

Les opérations qui mutent l'état (Command) sont séparées de celles qui le lisent (Query).

```java
// Commands — mutent, retournent void ou un identifiant
public interface ExecuteScenarioUseCase {
    ExecutionId execute(ExecuteScenarioCommand command); // retourne l'id, pas l'état complet
}
public interface CancelExecutionUseCase {
    void cancel(CancelExecutionCommand command);
}

// Queries — lisent, ne mutent pas
public interface GetExecutionStatusUseCase {
    ExecutionStatusView getStatus(ExecutionId id);
}
public interface ListCampaignReportsUseCase {
    List<CampaignReportSummary> list(ReportFilter filter);
}

// Handlers séparés — un handler par use case, pas de God Service
@Component
public class ExecuteScenarioCommandHandler implements ExecuteScenarioUseCase { ... }

@Component
public class GetExecutionStatusQueryHandler implements GetExecutionStatusUseCase { ... }
```

---

## 4. Event-Driven — Domain Events comme Contrat

Les events du domaine sont des faits immuables. Ils documentent ce qui s'est passé.

```java
// ✅ Nommage au passé — un fait accompli
public record TaskCompleted(
    ExecutionId executionId,
    TaskId taskId,
    String taskName,
    AgentId agentId,
    TaskResult result,
    Duration duration,
    Instant occurredAt          // toujours présent — quand ça s'est passé
) {}

// ✅ Un event = un fait précis. Pas d'event générique "TaskUpdated"
// ✅ Payload auto-suffisant — le consommateur n'a pas besoin d'appeler une API

// ❌ INCORRECT
public record TaskEvent(String type, Object payload) {} // type ambigu, payload non typé
```

**Règle de consommation dans ce projet (Spring Modulith) :**
```java
// @ApplicationModuleListener = async + transactionnel par défaut
// Utiliser pour tous les listeners inter-modules
@ApplicationModuleListener
public class ObservabilityEventListener {
    @EventListener
    public void on(TaskCompleted event) {
        meterRegistry.counter("task.completed", "taskName", event.taskName()).increment();
    }
}
```

---

## 5. Plugin Architecture — Open/Closed Principle à l'Échelle Système

Nouveau comportement = nouveau composant, zéro modification de l'existant.

```
Extension Points de ce projet :
  TaskExecutor     → @Preparation / @Injection / @Assertion + getSupportedTaskName()
  ExecutionTransport → @ConditionalOnProperty + implémentation de l'interface
  ReportPublisher  → @ConditionalOnProperty + implémentation de l'interface

Chaque point d'extension :
  ✅ Déclaré par une interface dans platform-application/port/
  ✅ Découvert automatiquement (Spring beans ou PluginLoader pour les JARs externes)
  ✅ Sélectionné par configuration, pas par code conditionnel
```

**Test de l'extensibilité :** si ajouter un nouveau transport nécessite de modifier
`RemoteExecutionEngine` ou `TaskDispatcher` → le design est fermé à l'extension.

---

## 6. Saga Pattern — Orchestration de Workflow Long

Une campagne est une saga : séquence d'étapes avec compensation possible.

```
Saga : ExecuteCampaign
  Step 1 : PREPARATION phase
    Success → continuer
    Failure → publier ScenarioFailed (pas de compensation — préparation stateless)

  Step 2 : INJECTION phase
    Success → continuer
    Failure → continuer vers ASSERTION (toujours — voir spec 02)
              + publier InjectionFailed dans le rapport

  Step 3 : ASSERTION phase
    Toujours exécutée — même si INJECTION a échoué
    Produit le Verdict final : SUCCESS | WARNING | FAILED

  Step 4 : REPORTING
    Toujours exécutée — le rapport documente ce qui s'est passé
```

**Restart (Orchestrator crash) :**
```
Pas de reprise partielle — ScenarioRestartSignal broadcast vers tous les agents.
Les agents font leur cleanup (libération ressources stateful).
Le scénario repart de zéro (spec 04, section 7).
```

---

## 7. Strangler Fig — Stratégie de Migration

Pour migrer progressivement sans casser l'existant (applicable à ce projet
pour la transition `TaskDefinition → StepDefinition`, `TaskMessage → TaskExecutionRequest`).

```
Phase 1 : Ajouter le nouveau concept (StepDefinition) sans supprimer l'ancien
Phase 2 : Migrer les consommateurs un par un vers le nouveau concept
Phase 3 : Marquer l'ancien concept @Deprecated
Phase 4 : Supprimer l'ancien concept quand plus aucun consommateur

Règle : jamais de Phase 4 sans que la Phase 2 soit complète pour tous les modules.
Voir roadmap.md Phase de cleanup pour la liste des suppressions planifiées.
```

---

## 8. Bounded Context — Frontières de Modules

Chaque module Spring Modulith est un Bounded Context avec son propre vocabulaire.

```
platform-execution-engine
  Vocabulaire : ExecutionPlan, ExecutionStep, DAG, dagLevel, Phase
  Ne parle pas de : Gatling, WireMock, Kafka, Report

platform-injection-gatling
  Vocabulaire : Simulation, LoadModel, OpenInjectionStep, InjectionResult
  Ne parle pas de : ExecutionPlan, DAG, AgentRegistry

platform-reporting
  Vocabulaire : CampaignReport, Verdict, Evidence, ReportPublisher
  Ne parle pas de : GatlingRunner, TaskExecutor, Transport
```

**Règle :** si un module importe un concept d'un autre module directement
(hors events) → les frontières sont mal définies.

---

## 9. Checklist Architect / System Designer

Avant de valider un PDR ou une décision architecturale :

```
[ ] Le nouveau composant a une interface dans application/port/ ?
[ ] Le domaine reste sans dépendance framework ?
[ ] La communication inter-modules passe par des events ?
[ ] L'extensibilité ne nécessite pas de modifier l'existant ?
[ ] Les erreurs prévisibles sont des résultats, pas des exceptions ?
[ ] Le composant a un seul niveau d'abstraction (pas de mélange logique métier + infra) ?
[ ] Le Bounded Context du module est respecté (pas de fuite de concepts) ?
[ ] Un ADR est produit si une interface publique change ?
```
