# CLAUDE.md — Performance Engineering Platform

> Ce fichier est la première chose à lire après `.claude/workspace/session-state.md`.
> Il contient les règles permanentes, la stack, et la table de routing.
> Ne pas modifier sans décision explicite de l'Architect.

---

## 1. Contexte Projet

**Nom** : Performance Engineering Platform
**Objectif** : Plateforme de test de performance distribuée — mode LOCAL ou DISTRIBUTED avec le même artefact.
**Stack** : Java 25 / Spring Boot 4.x / Gatling / PostgreSQL / Transport pluggable
**Statut** : Voir `.claude/workspace/session-state.md` (état exact) + `.claude/knowledge/roadmap.md` (phases)

---

## 2. Ordre de Lecture au Démarrage de Session

```
TOUJOURS lire dans cet ordre :
  1. .claude/workspace/session-state.md    ← état exact + Issue active
  2. CLAUDE.md                             ← ce fichier (règles)
  3. .claude/workspace/progress.md         ← trouver / confirmer l'Issue active (NON chargé en contexte IA depuis Phase B)
  4. .claude/workspace/current-issue.md    ← détail de l'Issue active (SEUL fichier à lire)
  5. .claude/agents/<role>.md              ← comportement de ton rôle

NE PAS lire les autres fichiers sauf si explicitement listés dans session-state.md.
current-task.md est un scratchpad secondaire — ne pas le lire sauf si Architect/Reviewer
travaille sur une décision hors-Issue.
```

---

## 3. Les 4 Agents — Activation

| Agent | Fichier | Invocation CLI | Quand |
|---|---|---|---|
| **System Designer** | `.claude/agents/system-designer.md` | `@system-designer` ou `.claude/scripts/agent.sh system-designer` | Avant tout développement — crée PDRs + Issues |
| **Architect** | `.claude/agents/architect.md` | `@architect` ou `.claude/scripts/agent.sh architect` | Décisions archi, ADRs, escalades |
| **Developer** | `.claude/agents/developer.md` | `@developer` ou `.claude/scripts/agent.sh developer` | Implémente l'Issue de `.claude/workspace/current-issue.md` |
| **Reviewer** | `.claude/agents/reviewer.md` | `@reviewer` ou `.claude/scripts/agent.sh reviewer` | Après chaque Issue IN REVIEW — rapport uniquement |
| **Tester** | `.claude/agents/tester.md` | `@tester` ou `.claude/scripts/agent.sh tester` | Tests d'intégration + E2E |

**Reprise sans contexte** → `.claude/prompts/session-bootstrap.md`
**Commandes slash** → `/next` `/done` `/review`
**Tracker d'avancement** → `.claude/workspace/progress.md` (scripts uniquement)
**Workflow complet** → `.claude/guides/agent-orchestration.md`

---

## 4. Stack Technique (NON NÉGOCIABLE)

| Couche | Technologie | Version |
|---|---|---|
| Runtime | Java | 25 (LTS) — Virtual Threads natifs |
| Framework | Spring Boot | 4.x |
| Modularity | Spring Modulith | dernière stable |
| Build | Maven | multi-module |
| Load injection | Gatling | 3.x Java DSL uniquement |
| Persistence | PostgreSQL | 15+ |
| Messaging | `ExecutionTransport` abstraction | — |
| Reporting | OpenHTMLToPDF | — |
| Observability | Micrometer + OpenTelemetry | — |
| Config | Jackson + SnakeYAML | — |
| Tests | JUnit 5 + Mockito + Testcontainers | — |

---

## 5. Règles Architecturales (NON NÉGOCIABLES)

| Règle | Violation détectée par |
|---|---|
| `platform-domain` : 0 annotation Spring/JPA/Jackson | ArchUnit test |
| Modules Modulith : communication via events uniquement | ArchUnit test |
| Nouveau `TaskExecutor` : Spring Bean auto-découverte, pas de if/switch | Code review |
| Nouveau transport : `@ConditionalOnProperty` uniquement | Code review |
| Modification interface publique : ADR obligatoire | Reviewer |
| `ExecutionContext` : immuable, `with()` uniquement | Code review |
| Tout I/O bloquant : Virtual Threads | Code review |

---

## 6. Structure Maven

```
performance-platform/
├── platform-domain/           (0 dépendance framework)
├── platform-application/      (use cases, ports)
├── platform-infrastructure/   (adapters)
├── platform-scenario-dsl/
├── platform-execution-engine/
├── platform-agent-runtime/
├── platform-transport/
├── platform-injection-gatling/
├── platform-assertion/
├── platform-reporting/
├── platform-observability/
├── platform-deployment/
└── platform-app/              (Spring Boot main)
```

Règles de dépendance : `platform-domain` ← `platform-application` ← tout le reste.
Jamais de dépendance cyclique. Voir `.claude/knowledge/architecture.md` section 3.

---

## 7. Conventions de Code

**Nommage** — référence : `.claude/knowledge/glossary.md`
- Interfaces : nom du concept (`ExecutionTransport`, `TaskExecutor`)
- Implémentations : préfixe technologie (`KafkaExecutionTransport`)
- Events : passé (`TaskCompleted`, `AgentRegistered`)
- Value Objects : records Java 25 immuables

**Packages** :
```
com.performance.platform.<module>/
  ├── domain/       (entités, records, events — 0 Spring)
  ├── application/  (use cases, ports in/out)
  └── infrastructure/ (adapters, config Spring)
```

**Logging** : `log.info("action={} executionId={} taskId={}", action, id, taskId)` — toujours avec contexte.

---

## 8. Table de Routing — Fichiers à Charger par Sujet

> Charger UNIQUEMENT les fichiers pertinents pour la tâche courante.

| Sujet | Fichier | Section |
|---|---|---|
| Vocabulaire ambigu | `.claude/knowledge/glossary.md` | terme concerné |
| DSL YAML / parsing | `.claude/knowledge/specs/01-scenario-dsl.md` | selon besoin |
| Orchestration / DAG | `.claude/knowledge/specs/02-execution-engine.md` | selon besoin |
| Nouveau TaskExecutor | `.claude/knowledge/specs/03-task-framework.md` | — |
| Nouveau TaskExecutor (pattern) | `.claude/knowledge/skills/task-executor-pattern.md` | complet |
| Agent lifecycle | `.claude/knowledge/specs/04-agent-runtime.md` | — |
| Transport | `.claude/knowledge/specs/05-transport-layer.md` | — |
| Gatling | `.claude/knowledge/specs/06-injection-gatling.md` | — |
| Gatling (patterns code) | `.claude/knowledge/skills/gatling-dsl.md` | — |
| Assertions | `.claude/knowledge/specs/07-assertion-framework.md` | — |
| Reporting | `.claude/knowledge/specs/08-report-engine.md` | — |
| Docker / K8s | `.claude/knowledge/specs/09-deployment.md` | — |
| Architecture hexagonale | `.claude/knowledge/skills/hexagonal-architecture.md` | complet |
| Spring Modulith | `.claude/knowledge/skills/spring-modulith.md` | complet |
| Stratégie de tests | `.claude/knowledge/skills/testing-strategy.md` | complet |
| Interfaces implémentées | `.claude/workspace/interfaces-registry.md` | module concerné |
| Décisions passées | `.claude/workspace/decisions-log.md` | sujet concerné |
| Issues connues | `.claude/workspace/known-issues.md` | — |
| Recommandations Reviewer en attente | `.claude/workspace/recommendations-tracking.md` | complet |
| Découpage des tâches | `.claude/workspace/task-breakdown.md` | phase concernée |
| Dépendances inter-tâches | `.claude/workspace/dependency-map.md` | — |
| Workflow agents | `.claude/guides/agent-orchestration.md` | — |
| Dev-Loop autonome | `.claude/scripts/dev-loop.sh` | — |
| Permissions | `.claude/settings.json` | complet |
| Plugin JAR externe | `.claude/knowledge/specs/03-task-framework.md` | section 7 |
| Annotations @Preparation/@Injection/@Assertion | `.claude/knowledge/adr/ADR-007-plugin-jar-system.md` | — |
| Priorité config env vs properties | `.claude/knowledge/adr/ADR-006-runtime-config-priority.md` | — |
| Agents spécialisés + filtre local | `.claude/knowledge/adr/ADR-008-specialized-agent-filter.md` | — |
| Consumer group Kafka par agent | `.claude/knowledge/adr/ADR-009-kafka-consumer-group-per-agent.md` | — |
| PartialExecutionContext | `.claude/knowledge/adr/ADR-010-partial-execution-context.md` | — |
| Multi-claim orchestrateur | `.claude/knowledge/adr/ADR-011-multi-claim-all-complete.md` | — |
| AgentLifecycleEvent séparé de ExecutionEvent | `.claude/knowledge/adr/ADR-012-agent-lifecycle-event.md` | — |
| Spring-first infra | `.claude/knowledge/adr/ADR-013-spring-first-infrastructure.md` | — |
| Config datasources | `.claude/knowledge/adr/ADR-014-datasource-configuration.md` | — |
| Avancement PDRs/Issues | `.claude/workspace/progress.md` | tableau Issues (scripts uniquement) |
| Détail d'un PDR | `.claude/workspace/pdr/PDR-XXX.md` | selon Issue active |
| Détail d'une Issue | `.claude/workspace/issues/ISSUE-XXX.md` | Issue active uniquement |

---

## 9. Commandes de Référence

```bash
mvn clean install                                     # build complet
mvn test -pl platform-xxx -q                         # tests d'un module
mvn verify -P integration-tests                       # tests d'intégration
mvn compile -pl platform-xxx 2>&1 | grep -i warn     # vérifier warnings
java -jar platform-app/target/*.jar --runtime.mode=LOCAL
MODE=ORCHESTRATOR java -jar platform-app/target/*.jar
MODE=AGENT java -jar platform-app/target/*.jar
bash .claude/scripts/dev-loop.sh                     # boucle autonome Developer→Reviewer→Tester
bash .claude/scripts/dev-loop.sh --dry-run            # dry-run du dev-loop
bash .claude/scripts/dev-loop.sh --max 3              # limiter à 3 Issues
```

---

## 10. Signaux d'Alarme → Escalade Architect Immédiate

- Annotation Spring/JPA dans `platform-domain` ou `platform-plugin-api`
- Appel direct entre deux modules Spring Modulith
- `if/switch` sur le `name` ou le type dans le registre des executors
- `TaskExecutor` sans annotation `@Preparation`, `@Injection`, ou `@Assertion`
- Modification de `TaskExecutor`, `ExecutionTransport`, `ReportPublisher`, ou des 3 annotations
- `executionContext.store.put(...)` — mutation directe du contexte
- Nouvelle dépendance Maven non dans `.claude/knowledge/constraints.md`
- Variable d'env ignorée au profit d'une property hardcodée (env var doit être prioritaire)
- Utilisation de `TaskDefinition` — remplacé par `StepDefinition` (ADR-008)
- Utilisation de `TaskType` enum — remplacé par `taskName` String + annotations
- Utilisation de `AgentAllocator` — supprimé (ADR-008)
- Utilisation de `TaskMessage` — remplacé par `TaskExecutionRequest`
- Utilisation de `transport.sendTask(agentId, ...)` — remplacé par `transport.dispatchTask(...)`
