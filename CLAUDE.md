# CLAUDE.md — Performance Engineering Platform

> Ce fichier contient les règles permanentes, la stack, et la table de routing.
> Ne pas modifier sans décision explicite de l'Architect.

---

## 0. Règle Globale — Concision

- **Sois concis.** Pas de paraphrases, pas de résumés redondants, pas de formules de politesse creuses.
- **Pas de spéculation.** Si tu ne sais pas, dis-le en une phrase, ne brode pas.
- **Vérifie avant d'affirmer.** Ne déduis pas l'état du système depuis un résumé de notification — lis le fichier ou vérifie avec `git log`/`git status` avant de commenter.
- **TON minimal.** Une réponse réussie tient en ≤ 5 lignes quand la situation est claire.

---

## 1. Contexte Projet

**Nom** : Performance Engineering Platform
**Objectif** : Plateforme de test de performance distribuée — mode LOCAL ou DISTRIBUTED avec le même artefact.
**Stack** : Java 25 / Spring Boot 4.x / Gatling / PostgreSQL / Transport pluggable
**Statut** : Voir `bash .claude/scripts/progress-status.sh`

---

## 2. Point d'Entrée Unique au Démarrage

```
TOUJOURS lire dans cet ordre :
  1. .claude/workspace/current-issue.md    ← SEUL fichier. Contient TOUT : specs, signatures, critères, feedbacks
  2. .claude/agents/<role>.md              ← comportement de ton rôle (lit aussi current-issue.md)

NE PAS lire progress.md, PDRs, ni recommendations-tracking.md.
Ces fichiers sont gérés EXCLUSIVEMENT par les scripts :
  - issue-start.sh / issue-finish.sh / issue-review.sh / issue-next.sh
  - progress-status.sh
Les agents n'ont pas besoin de ces fichiers pour travailler.
```

---

## 3. Les Agents — Activation

| Agent | Fichier | Invocation CLI | Quand |
|---|---|---|---|
| **System Designer** | `.claude/agents/system-designer.md` | `@system-designer` | Avant tout développement — crée PDRs + Issues |
| **Architect** | `.claude/agents/architect.md` | `@architect` | Décisions archi, ADRs, escalades |
| **Developer** | `.claude/agents/developer.md` | `@developer` | Implémente l'Issue dans `current-issue.md` — `issue-start.sh` → code → `issue-finish.sh` |
| **Reviewer** | `.claude/agents/reviewer.md` | `@reviewer` | Review le code de `current-issue.md` — `git diff` → verdict → `issue-review.sh` |
| **Tester** | `.claude/agents/tester.md` | `@tester` | Tests d'intégration + E2E |

**Scripts de workflow** (gèrent progress.md et session-state.md) :
- `issue-start.sh` — WAITING → IN_PROGRESS + crée current-issue.md
- `issue-finish.sh` — IN_PROGRESS → IN_REVIEW
- `issue-review.sh APPROVED|CHANGES_REQUESTED` — enregistre le verdict
- `issue-next.sh` — APPROVED → DONE + archive + lance la prochaine

**Dev-Loop** → `bash .claude/scripts/dev-loop.sh`

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
├── core/          (domain, use-cases, plugin-api, transport-api, report-api)
├── execution/     (execution-engine, agent-runtime : generiques)
├── performance/   (campaign-engine, scenario-dsl, assertion, reporting)
├── infra/         (adaptateurs techniques, transports, YAML, Gatling, rendu, JPA)
├── bootstrap/     (platform-bootstrap : assemblage Spring Boot, REST/IHM/CLI)
├── distribution/  (Docker, Compose, K8s, exemples, applications SUT)
├── docs/knowledge/ (specifications, architecture, ADRs)
└── .claude/skills/<nom>/SKILL.md (workflows du harness)
```

Règles de dépendance : `platform-domain` ← `platform-use-cases` ← tout le reste.
`execution/` ne depend en production que des contrats de `core/`, jamais des
politiques de `performance/` ou des adaptateurs de `infra/` (ADR-045).
Jamais de dépendance cyclique. Voir `docs/knowledge/architecture.md` section 3.

---

## 7. Conventions de Code

**Nommage** — référence : `docs/knowledge/glossary.md`
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
| Vocabulaire ambigu | `docs/knowledge/glossary.md` | terme concerné |
| DSL YAML / parsing | `docs/knowledge/specs/01-scenario-dsl.md` | selon besoin |
| Orchestration / DAG | `docs/knowledge/specs/02-execution-engine.md` | selon besoin |
| Nouveau TaskExecutor | `docs/knowledge/specs/03-task-framework.md` | — |
| Nouveau TaskExecutor (pattern) | `.claude/skills/task-executor-pattern/SKILL.md` | complet |
| Agent lifecycle | `docs/knowledge/specs/04-agent-runtime.md` | — |
| Transport | `docs/knowledge/specs/05-transport-layer.md` | — |
| Gatling | `docs/knowledge/specs/06-injection-gatling.md` | — |
| Gatling (patterns code) | `.claude/skills/gatling-dsl/SKILL.md` | — |
| Assertions | `docs/knowledge/specs/07-assertion-framework.md` | — |
| Reporting | `docs/knowledge/specs/08-report-engine.md` | — |
| Docker / K8s | `docs/knowledge/specs/09-deployment.md` | — |
| Architecture hexagonale | `.claude/skills/hexagonal-architecture/SKILL.md` | complet |
| Spring Modulith | `.claude/skills/spring-modulith/SKILL.md` | complet |
| Stratégie de tests | `.claude/skills/testing-strategy/SKILL.md` | complet |
| Interfaces implémentées | `.claude/workspace/interfaces-registry.md` | module concerné |
| Décisions passées | `.claude/workspace/decisions-log.md` | sujet concerné |
| Issues connues | `.claude/workspace/known-issues.md` | — |
| Recommandations Reviewer/Architect en attente | `.claude/workspace/current-issue.md` section "Recommendations PENDING" (injecté par `issue-start.sh`) | — |
| Découpage des tâches | `.claude/workspace/task-breakdown.md` | phase concernée |
| Dépendances inter-tâches | `.claude/workspace/dependency-map.md` | — |
| Workflow agents | `.claude/guides/agent-orchestration.md` | — |
| Dev-Loop autonome | `.claude/scripts/dev-loop.sh` | — |
| Permissions | `.claude/settings.json` | complet |
| Plugin JAR externe | `docs/knowledge/specs/03-task-framework.md` | section 7 |
| Annotations @Preparation/@Injection/@Assertion | `docs/knowledge/adr/ADR-007-plugin-jar-system.md` | — |
| Priorité config env vs properties | `docs/knowledge/adr/ADR-006-runtime-config-priority.md` | — |
| Agents spécialisés + filtre local | `docs/knowledge/adr/ADR-008-specialized-agent-filter.md` | SUPERSEDED BY ADR-015 |
| Spécialisation configuration-driven + `agent.supported-tasks` + supression `agentTags` | `docs/knowledge/adr/ADR-015-configuration-driven-agent-specialization.md` | — |
| Consumer group Kafka par agent | `docs/knowledge/adr/ADR-009-kafka-consumer-group-per-agent.md` | — |
| PartialExecutionContext | `docs/knowledge/adr/ADR-010-partial-execution-context.md` | — |
| Multi-claim orchestrateur | `docs/knowledge/adr/ADR-011-multi-claim-all-complete.md` | — |
| AgentLifecycleEvent séparé de ExecutionEvent | `docs/knowledge/adr/ADR-012-agent-lifecycle-event.md` | — |
| Spring-first infra | `docs/knowledge/adr/ADR-013-spring-first-infrastructure.md` | — |
| Config datasources | `docs/knowledge/adr/ADR-014-datasource-configuration.md` | — |
| Modes d'accès (API/IHM/CLI) × mode runtime — AGENT n'expose rien | `docs/knowledge/specs/00-overview.md` | section "Modes d'Accès" (matrice canonique) |
| IHM serving + sécurité + AGENT = aucun serveur web (`WebApplicationType.NONE`) | `docs/knowledge/adr/ADR-019-web-ui-serving-and-security.md` | décision 4 |
| CLI headless `--scenario=` — réservé LOCAL/ORCHESTRATOR, jamais AGENT | `docs/knowledge/adr/ADR-021-headless-cli-run-mode.md` | — |
| Avancement PDRs/Issues | `.claude/workspace/progress.md` | tableau Issues (scripts uniquement) |
| Issue active (pointer) | `.claude/workspace/current-issue.md` | status, IssueFile, feedbacks |
| Détail d'un PDR | `.claude/workspace/pdr/PDR-XXX.md` | selon Issue active |
| Détail d'une Issue | `.claude/workspace/issues/ISSUE-XXX.md` | Issue active uniquement |

---

## 9. Commandes de Référence

```bash
mvn clean install                                     # build complet
mvn test -pl :platform-xxx -q                         # tests d'un module
mvn verify -P integration-tests                       # tests d'intégration
mvn compile -pl :platform-xxx 2>&1 | grep -i warn     # vérifier warnings
java -jar bootstrap/target/*.jar --runtime.mode=LOCAL
java -jar bootstrap/target/*.jar --scenario=path/to/scenario.yaml   # CLI headless (run-and-exit, exit 0/1/2, ADR-021)
MODE=ORCHESTRATOR java -jar bootstrap/target/*.jar
MODE=AGENT java -jar bootstrap/target/*.jar
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
- Nouvelle dépendance Maven non dans `docs/knowledge/constraints.md`
- Variable d'env ignorée au profit d'une property hardcodée (env var doit être prioritaire)
- Utilisation de `TaskDefinition` — remplacé par `StepDefinition` (ADR-008)
- Utilisation de `TaskType` enum — remplacé par `taskName` String + annotations
- Utilisation de `AgentAllocator` — supprimé (ADR-008)
- Utilisation de `TaskMessage` — remplacé par `TaskExecutionRequest`
- Utilisation de `transport.sendTask(agentId, ...)` — remplacé par `transport.dispatchTask(...)`
- Utilisation de `agentTags` dans un scénario YAML — supprimé, le routing est par `task:` name uniquement (ADR-015)
- Utilisation de `AGENT_TAGS` env var — remplacé par `AGENT_SUPPORTED_TASKS` (ADR-015)
- Auto-discovery des `supportedTaskNames` depuis les annotations — les noms viennent de la config `agent.supported-tasks` uniquement (ADR-015)
