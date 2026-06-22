# Roadmap — Performance Engineering Platform

> L'ordre des phases est obligatoire. Chaque phase est un prérequis de la suivante.
> Ne pas démarrer une phase si la précédente n'est pas terminée et validée.

---

## Phase 1 — Foundation (Domaine + DSL)

**Objectif** : Avoir un domaine propre et un parser YAML fonctionnel.

Livrables :
- [ ] Structure Maven multi-module complète (`pom.xml` parent + tous les modules)
- [ ] `platform-plugin-api` : module léger avec `TaskExecutor`, `ExecutionContext`, `TaskResult`, 3 annotations
- [ ] `platform-domain` : toutes les entités, value objects, events définis dans le glossaire
  Note : `StepDefinition` remplace `TaskDefinition`, `taskName` (String) remplace `TaskType` enum
- [ ] `platform-scenario-dsl` : parser YAML → `ScenarioDefinition`, validation complète
- [ ] `platform-scenario-dsl` : registry des `LoadModel` réutilisables
- [ ] Tests unitaires domaine (coverage > 90%)
- [ ] Exemple de scénario YAML valide (`examples/scenario-basic.yaml`)

Critères de done :
- Parser lit le scénario YAML de la spec et produit un `ScenarioDefinition` complet
- Erreurs de validation retournées avec champ + message
- 0 dépendance Spring dans `platform-domain`

---

## Phase 2 — Execution Engine LOCAL

**Objectif** : Exécuter un scénario complet en mode LOCAL sans distribution.

Livrables :
- [ ] `platform-execution-engine` : `ExecutionPlan` builder depuis `ScenarioDefinition`
- [ ] DAG resolver avec gestion des `dependsOn`
- [ ] `LocalExecutionEngine` : exécution phases PREPARATION → INJECTION → ASSERTION
- [ ] `TaskExecutorRegistry` : auto-découverte via Spring beans
- [ ] `ExecutionContext` immuable avec propagation entre tâches
- [ ] Retry configurable par tâche (attempts + backoff exponentiel)
- [ ] `platform-application` : use cases + ports définis
- [ ] Tests d'intégration : scénario complet de bout en bout

Critères de done :
- Un scénario avec dépendances complexes s'exécute dans le bon ordre
- Retry fonctionne sur échec simulé
- ExecutionContext propaged correctement entre phases

---

## Phase 3 — Task Executors

**Objectif** : Implémenter tous les types de tâches de la spec.

Livrables :
- [ ] `DatabaseTaskExecutor` (purge, populate, migration, backup, restore)
- [ ] `FilesystemTaskExecutor` (create, delete, upload, cleanup)
- [ ] `ShellTaskExecutor`
- [ ] `DockerTaskExecutor` (start, stop, pull)
- [ ] `KafkaConsumerTaskExecutor` (consume, monitor, count)
- [ ] `KafkaProducerTaskExecutor` (preload)
- [ ] `MockServerTaskExecutor` (WireMock embedded + external)
- [ ] Tests d'intégration avec Testcontainers pour DB, Kafka

Critères de done :
- Chaque executor : tests d'intégration verts avec Testcontainers
- Aucun executor ne référence un autre executor
- [ ] `PluginLoader` : scan du répertoire `/plugins`, chargement URLClassLoader
- [ ] `PluginRegistry` : registre unifié interne + externe, lookup par phase + name
- [ ] Test : JAR plugin externe chargé et executor résolu depuis le YAML

---

## Phase 4 — Gatling Integration

**Objectif** : Exécuter de vrais tests de charge Gatling depuis la plateforme.

Livrables :
- [ ] `platform-injection-gatling` : `GatlingTaskExecutor`
- [ ] `SimulationRunner` : lancement Gatling in-process avec Virtual Threads
- [ ] `LoadModelBuilder` pour tous les types (RAMP, CONSTANT, SPIKE, STAIR, SOAK, BURST, CUSTOM)
- [ ] `InjectionResult` parsé depuis les résultats Gatling (p50/p90/p95/p99, errorRate, throughput)
- [ ] Support protocoles : HTTP, HTTPS, WebSocket, Kafka, JMS, gRPC
- [ ] Exemple de simulation Gatling Java DSL incluse

Critères de done :
- Simulation Gatling s'exécute via `GatlingTaskExecutor`
- `InjectionResult` contient p95 < valeur cible mesurée
- Load model RAMP avec stages produit le profil attendu

---

## Phase 5 — Assertion Framework

**Objectif** : Évaluer les assertions post-injection.

Livrables :
- [ ] `AssertionExecutor` interface + `AssertionExecutorRegistry`
- [ ] `GatlingMetricAssertionExecutor` (errorRate, p95, p99, throughput)
- [ ] `DatabaseAssertionExecutor` (count, exists, custom SQL)
- [ ] `KafkaAssertionExecutor` (produced count, consumed count, lag)
- [ ] `HttpAssertionExecutor` (mock received calls, endpoint state)
- [ ] `FileAssertionExecutor` (exists, checksum)
- [ ] `AssertionResult` avec Evidence détaillée

Critères de done :
- Assertion PASSED et FAILED avec evidence testées
- `ExecutionContext` correctement lu par chaque executor

---

## Phase 6 — Reporting

**Objectif** : Générer des rapports complets et les publier.

Livrables :
- [ ] `platform-reporting` : `ReportEngine`
- [ ] Générateur HTML (OpenHTMLToPDF)
- [ ] Générateur PDF
- [ ] Générateur JSON
- [ ] `ReportPublisher` interface + `ConfluenceReportPublisher`
- [ ] `S3ReportPublisher`, `GitReportPublisher`
- [ ] Structure du rapport : metadata, env, preparation, injection, assertions, verdict

Critères de done :
- Rapport HTML lisible avec graphiques Gatling embarqués
- PDF généré depuis HTML
- Confluence publisher fonctionnel (testé avec mock Confluence)

---

## Phase 7 — Transport Layer + Distribution

**Objectif** : Activer le mode DISTRIBUTED.

Livrables :
- [ ] `ExecutionTransport` interface complète
- [ ] `SocketExecutionTransport`
- [ ] `RabbitMQExecutionTransport`
- [ ] `KafkaExecutionTransport`
- [ ] `GrpcExecutionTransport`
- [ ] `platform-agent-runtime` : `AgentRuntime`, heartbeat, reconnect
- [ ] `RemoteExecutionEngine` : allocation agents, dispatch, agrégation
- [ ] `AgentRegistry` avec sélection par tags
- [ ] Tests d'intégration distributed (docker-compose : orchestrator + 2 agents)

Critères de done :
- Scénario DISTRIBUTED s'exécute sur 2 agents avec transport Kafka
- Perte d'un agent → reconnect automatique
- Changement de transport via config seule

---

## Phase 8 — Observabilité + Sécurité

**Objectif** : Plateforme production-ready.

Livrables :
- [ ] Métriques Micrometer exposées (toutes celles définies dans .claude/knowledge/constraints.md CNF-04)
- [ ] Traces OpenTelemetry sur toutes les tâches et phases
- [ ] Logs JSON structurés avec tous les champs requis
- [ ] TLS sur tous les transports
- [ ] OAuth2/JWT sur l'API REST
- [ ] mTLS configuré pour la communication inter-agents

---

## Phase 9 — Containerisation + Kubernetes

**Objectif** : Déploiement cloud-native.

Livrables :
- [ ] `Dockerfile` multi-stage, image < 300MB, user non-root
- [ ] `docker-compose.yaml` pour dev local (orchestrator + 2 agents + kafka + postgres)
- [ ] K8s manifests : Deployment, StatefulSet, ConfigMap, Secret, HPA
- [ ] Documentation déploiement K8s

---

## Phase 10 — Documentation + Exemples

**Objectif** : Projet maintenable et extensible.

Livrables :
- [ ] Guide développeur : ajouter un TaskExecutor
- [ ] Guide développeur : ajouter un Transport
- [ ] Guide développeur : ajouter un ReportPublisher
- [ ] 3 scénarios YAML d'exemple (simple, avec dépendances, distribué)
- [ ] Diagrammes de séquence (PlantUML) pour les flows principaux
- [ ] Tous les ADRs complétés
