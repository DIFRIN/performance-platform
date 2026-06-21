# Session State



> CE FICHIER EST CRITIQUE. Mis a jour a la FIN de chaque session. Lu EN PREMIER au demarrage.
> Permet la reprise exacte sans re-lire tout le projet.

---

## Etat Courant

**Date derniere session** : 2026-06-21
**Agent actif** : [x] System Designer | [ ] Developer | [ ] Architect | [ ] Reviewer | [ ] Tester
**Issue active** : aucune (System Designer a corrige 4 incoherences PDR-024/ISSUE-100/101 — prochaine : ISSUE-086 ou ISSUE-092 ou ISSUE-098)
**Statut issue** : [x] WAITING
**PDR parent** : PDR-020 (premier a demarrer)

---

## Reprise Exacte

**Derniere action** :
System Designer — Correction de 4 incoherences signalees par l'Architect dans PDR-024 / ISSUE-100 / ISSUE-101 :
- INC-1 [BLOQUANT] URLs techniques inline dans blocs `gatling:` supprimees.
  - iot-dispatcher-local (PDR-024) : `inject-load` n'est plus Gatling avec baseUrl Kafka 9093 — c'est un step `kafka-producer` (`cluster: iot-sut`, `topic: iot-commands`). Gatling ne produit pas de Kafka.
  - iot-dispatcher-distributed (ISSUE-100) : `inject-load` Gatling utilise `target: iot-dispatcher-health` + `path: health` (au lieu de baseUrl 8083).
  - device-api local+distributed (PDR-024+ISSUE-101) : Gatling utilise `target: device-api` + `path: submit-event` (au lieu de baseUrl 8082 / device-api:8082).
- INC-2 [MAJEUR] step `purge-events` (table events inexistante) renomme `reset-devices` (PURGE table `devices`), commentaire corrige. ISSUE-101 : step `purge-and-reseed` (qui ne faisait que POPULATE) scinde en `reset-devices` (PURGE) + `seed-devices` (POPULATE). PURGE justifie par ON CONFLICT DO NOTHING du script de seed.
- INC-3 [MAJEUR] conflit port 8082 confirme reel (plateforme docker-compose.yaml mappe host 8082→agent-2). device-api SUT change de 8082 → 8084 dans PDR-024 (docker-compose-sut, SERVER_PORT, ports, commentaires, base-url) et ISSUE-101 (config + commentaires).
- INC-4 [BLOQUANT] `scriptPath: classpath:sql/V2__seed_10k_devices.sql` invalide → Option A retenue : copie du script SUT vers `platform-app/src/main/resources/sql/seed-sut-devices.sql` (sans prefixe Flyway), `scriptPath: classpath:sql/seed-sut-devices.sql`. Fichier ajoute dans "Fichiers a Creer" de ISSUE-101.

A SIGNALER (hors perimetre d'edition de cette tache — a corriger par le Developer/SD ulterieurement) :
- ISSUE-099 (docker-compose-sut) ligne ~39 : device-api encore liste a 8082 → doit passer a 8084 (coherence INC-3).
- ISSUE-102 (README) lignes ~48/81/177 : device-api encore a 8082 → doit passer a 8084.

Contexte initial (sessions precedentes) :
System Designer — 5 PDRs (020..024) + 17 Issues (086..102) crees.
- PDR-020 : KafkaClusterRegistry + refactoring executors Kafka → Spring KafkaTemplate
- PDR-021 : Spring Kafka transport (KafkaTemplate + DynamicKafkaListenerRegistry)
- PDR-022 : HttpTargetRegistry + HttpClientTaskExecutor + refactoring MockServer/HttpMockAssertion
- PDR-023 : SUT standalone (iot-dispatcher + device-api + DB schema)
- PDR-024 : docker-compose-sut + scenarios IoT LOCAL/DISTRIBUTED + README

Nouvelles features cles :
- Pattern "Named Registry" etendu a Kafka (cluster: ref) et HTTP (target: ref)
- Noms logiques de topics ET chemins HTTP resolus depuis application-*.yaml (pas de URL inline dans scenarios)
- Meme scenario.yaml utilisable sur tous les environnements (seul application-*.yaml change)
- Spring Kafka remplace les raw clients dans les executors ET dans le transport
- Deux services SUT realistes (iot-dispatcher et device-api) pour les demos

**Prochaine action** :
Developer peut demarrer en parallele :
  1. ISSUE-086 (P1, M, aucune dep) — KafkaClusterRegistry
  2. ISSUE-092 (P1, M, aucune dep) — HttpTargetRegistry
  3. ISSUE-098 (P0, S, aucune dep) — DB schema SUT

**Fichiers modifies** (cette session) :
- .claude/pdr/PDR-020-kafka-cluster-registry.md (cree)
- .claude/pdr/PDR-021-spring-kafka-transport.md (cree)
- .claude/pdr/PDR-022-http-target-registry.md (cree)
- .claude/pdr/PDR-023-sut-example-services.md (cree)
- .claude/pdr/PDR-024-scenarios-docker-compose-sut.md (cree)
- .claude/issues/ISSUE-086..102.md (17 fichiers crees)
- .claude/progress.md (PDR-020..024 + ISSUE-086..102 ajoutas)
- .claude/context/interfaces-registry.md (nouvelles interfaces PLANNED, classes refactorees marquees BREAKING)
- .claude/session-state.md (ce fichier)

**Blocages** :
- Memes blocages techniques que sessions precedentes (Spring Boot 4.0.0 + JUnit 5.11.4, Docker absent)
- spring-kafka a verifier dans le classpath plateforme (peut necessiter ajout dans pom.xml si non transitif)

---

## Historique Sessions (1 ligne par session)

| Date | Agent | Issue | Action | Resultat |
|---|---|---|---|---|
| 2026-06-20 | Developer | ISSUE-077 | platform-app: pom.xml (11 modules) + @SpringBootApplication + @Modulith + 4 tests, fat JAR 131 MB. | IN REVIEW |
| 2026-06-20 | Reviewer | ISSUE-077 | Review APPROVED: 0 bloquant, 0 recommandation. 4 tests OK. Commit. | DONE |
| 2026-06-20 | Developer | ISSUE-078 | RuntimeMode + RuntimeRole + RuntimeModeResolver + EnvironmentPostProcessor + 24 tests, 28 total OK. | IN REVIEW |
| 2026-06-20 | Developer | ISSUE-079 | ScenarioController + 2 DTOs + ApiExceptionHandler + 11 tests. 43 total OK, BUILD SUCCESS. Jackson 2.x excluded, bytebuddy experimental. | IN REVIEW |
| 2026-06-20 | Developer | ISSUE-079 | 3 recommandations APPLIED (CRAFT-02, CRAFT-08, CRAFT-07). Tests OK. Awaiting re-review. | — |
| 2026-06-20 | Reviewer | ISSUE-079 | Re-review: 3 recommandations CONFIRMED (CRAFT-02/CRAFT-08/CRAFT-07). Commit. | DONE |
| 2026-06-20 | Developer | ISSUE-080 | PluginBootstrap + PluginProperties + 8 tests + @EnableConfigurationProperties. 51 tests OK, BUILD SUCCESS. | IN REVIEW |
| 2026-06-20 | Developer | ISSUE-081 | application.yaml (common/health), 3 profiles (local/orchestrator/agent), SecurityConfiguration OAuth2/JWT, ConfigProfilesTest (14 tests). 65 tests OK, 0 warning. | IN REVIEW |
| 2026-06-20 | Developer | ISSUE-082 | LocalFlowE2ETest + e2e-local.yaml + RawJpaExecutionRepository. Testcontainers PostgreSQL, Flyway, manual wiring. 66 tests OK, BUILD SUCCESS. | IN REVIEW |
| 2026-06-20 | Reviewer | ISSUE-082 | Review APPROVED: 0 bloquant, 1 recommandation PENDING (VERSION Testcontainers 1.20.4→1.20.6). | APPROVED |
| 2026-06-20 | Reviewer | ISSUE-082 | Re-review: [VERSION] CONFIRMED (Testcontainers 1.20.6 verified platform-app/pom.xml). Commit. | DONE |
| 2026-06-20 | Developer | ISSUE-083 | platform-deployment: pom.xml + Dockerfile multi-stage + .dockerignore. Maven BUILD SUCCESS, 0 warning. | IN REVIEW |
| 2026-06-20 | Reviewer | ISSUE-083 | Review APPROVED: 0 bloquant, 1 recommandation PRECISION PENDING | APPROVED |
| 2026-06-20 | Reviewer | ISSUE-083 | Re-review: [PRECISION] CONFIRMED (suppression !platform-app/target/performance-platform.jar + commentaire). Commit. | DONE |
| 2026-06-20 | Developer | ISSUE-084 | platform-deployment/docker/docker-compose.yaml (5 services: postgres, kafka KRaft, orchestrator, agent-1, agent-2). IN REVIEW. | IN REVIEW |
| 2026-06-20 | Developer | ISSUE-085 | 6 manifests K8s (orchestrator-statefulset, agent-deployment, agent-hpa, configmap, secret-template, service). YAML valide, 0 gRPC, probes OK, HPA 2-20 CPU 70%. | IN REVIEW |
| 2026-06-20 | Reviewer | ISSUE-085 | Review APPROVED: 0 bloquant, 1 recommandation [PRECISION] PENDING (terminologie "headless" service.yaml). DB_USERNAME verifie conforme application-orchestrator.yaml:41. | APPROVED |
| 2026-06-20 | Reviewer | ISSUE-085 | Re-review: [PRECISION] CONFIRMED (headless→external service placeholder). PDR-019 DONE. Projet termine. | DONE |
| 2026-06-21 | System Designer | PDR-024/ISSUE-100/101 | Correction 4 incoherences Architect: INC-1 (URLs inline gatling→target/kafka-producer), INC-2 (purge-events→reset-devices), INC-3 (device-api 8082→8084 conflit agent-2), INC-4 (scriptPath classpath:sql/seed-sut-devices.sql + SQL copie). | DONE |
