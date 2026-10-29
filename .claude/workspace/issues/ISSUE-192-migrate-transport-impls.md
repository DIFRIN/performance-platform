# ISSUE-192 — Migrate 5 Transport Implementations to Narrow Ports

**PDR** : PDR-052
**Module** : `platform-transport`
**Statut** : DONE
**Priorité** : P2
**Bloquée par** : ISSUE-191
**Estime** : L (3-6h)

---

## Objectif

Migrer InMemory, Kafka, RabbitMQ, HTTP, Socket vers les ports étroits (A4). Chaque
implémentation n'implémente que ce qu'elle supporte ; capacité non supportée →
`TransportException` claire.

## Fichiers à Modifier

```
infra/platform-transport/src/main/java/com/performance/platform/transport/
  ├── inmemory/InMemoryExecutionTransport.java
  ├── kafka/KafkaExecutionTransport.java
  ├── rabbitmq/RabbitMQExecutionTransport.java
  ├── http/HttpExecutionTransport.java
  └── socket/SocketExecutionTransport.java
```

## Règles Spécifiques

- Les 5 classes implémentent la façade composite `ExecutionTransport` (ou directement
  les ports qu'elles supportent) ; `getType()` supprimé.
- Aucun changement de comportement (refactor mécanique).

## Critères de Done

- [ ] 5 implémentations migrées, `getType()` absent
- [ ] `mvn test -pl :platform-transport -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-192 → DONE
