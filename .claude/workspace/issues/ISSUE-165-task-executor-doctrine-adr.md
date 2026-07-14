# ISSUE-165 — Task Executor Doctrine ADR-022 + Package Documentation

**PDR** : PDR-041
**Module** : `platform-infrastructure`
**Statut** : DONE
**Priorite** : P3 (documentation pure, aucun changement de code)
**Bloquee par** : —
**Estime** : S (< 1h)

---

## Objectif

Créer l'ADR-022 documentant la doctrine Task Executor (executor dédié vs ShellTaskExecutor) et ajouter un `package-info.java` dans le package executor listant tous les executors disponibles.

## Fichiers à Créer

```
.claude/knowledge/adr/
  └── ADR-022-task-executor-doctrine.md   — doctrine dédié vs shell

platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/
  └── package-info.java                   — liste des executors disponibles
```

## Fichiers à Modifier

```
platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/database/
  └── DatabaseTaskExecutor.java           — ajouter mention des garde-fous dans la Javadoc

platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/docker/
  └── DockerTaskExecutor.java             — idem

platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/fs/
  └── FilesystemTaskExecutor.java         — idem

platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/shell/
  └── ShellTaskExecutor.java              — mentionner le rôle de fallback
```

## ADR-022 — Structure

Suivre le format standard des ADRs du projet :

```markdown
# ADR-022 — Task Executor Doctrine: Dedicated vs Shell

**Status** : ACCEPTED
**Date** : 2026-07-13
**Deciders** : System Designer, Architect

## Context
9 TaskExecutors existent. Question récurrente : "pourquoi ne pas tout faire via Shell ?"

## Decision
- Executors dédiés : pour opérations critiques avec garde-fous (validation, lifecycle, sandboxing)
- ShellTaskExecutor : pour one-off, prototypage, commandes sans SDK Java
- Critères de décision : validation requise ? lifecycle stateful ? réutilisable et critique ?

## Consequences
- ShellTaskExecutor reste le fallback universel
- Chaque executor dédié documente ses garde-fous dans sa Javadoc
- Pas de suppression : l'audit n'a trouvé aucune duplication
```

## package-info.java — executor

```java
/**
 * TaskExecutor implementations — adapters between scenario steps and external systems.
 *
 * <h2>Executors disponibles</h2>
 * <table>
 *   <tr><th>Classe</th><th>taskName</th><th>Phase</th><th>Quand l'utiliser</th></tr>
 *   <tr><td>DatabaseTaskExecutor</td><td>database</td><td>PREPARATION</td>
 *       <td>Purge/populate DB avec validation SQL</td></tr>
 *   <tr><td>DockerTaskExecutor</td><td>docker</td><td>PREPARATION</td>
 *       <td>Conteneurs avec healthcheck et cleanup</td></tr>
 *   <tr><td>FilesystemTaskExecutor</td><td>filesystem</td><td>PREPARATION</td>
 *       <td>Opérations fichier avec sandboxing</td></tr>
 *   <tr><td>HttpClientTaskExecutor</td><td>http-client</td><td>PREPARATION</td>
 *       <td>Requêtes HTTP avec timeout/retry</td></tr>
 *   <tr><td>KafkaConsumerTaskExecutor</td><td>kafka-consumer</td><td>PREPARATION</td>
 *       <td>Consommation Kafka avec consumer groups</td></tr>
 *   <tr><td>KafkaProducerTaskExecutor</td><td>kafka-producer</td><td>PREPARATION</td>
 *       <td>Production Kafka avec cluster registry</td></tr>
 *   <tr><td>MockServerTaskExecutor</td><td>mock-server</td><td>PREPARATION</td>
 *       <td>Mock HTTP avec WireMock lifecycle</td></tr>
 *   <tr><td>ShellTaskExecutor</td><td>shell</td><td>PREPARATION</td>
 *       <td>Fallback : commandes shell génériques</td></tr>
 * </table>
 *
 * <h2>Doctrine</h2>
 * Voir ADR-022 pour la doctrine dédié vs Shell.
 */
package com.performance.platform.infrastructure.executor;
```

## Criteres de Done

- [ ] ADR-022 créé dans `.claude/knowledge/adr/ADR-022-task-executor-doctrine.md`
- [ ] `package-info.java` créé listant les 8 executors avec leur taskName
- [ ] Javadoc de 4 executors mise à jour mentionnant leurs garde-fous
- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur (aucun changement de code)
