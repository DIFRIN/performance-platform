# ISSUE-028 — Transport properties + TransportConfiguration

**PDR** : PDR-008
**Module** : `platform-transport`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-025
**Estime** : M

---

## Objectif

Créer les `@ConfigurationProperties` des 4 transports et la classe `TransportConfiguration`
avec les `@Bean` conditionnels (squelette — beans complétés par les issues suivantes).

## Fichiers à Créer

```
platform-transport/src/main/java/com/performance/platform/transport/
  └── TransportType.java                       — enum des transports disponibles

platform-transport/src/main/java/com/performance/platform/transport/config/
  ├── KafkaTransportProperties.java
  ├── RabbitMQTransportProperties.java
  ├── HttpTransportProperties.java
  ├── SocketTransportProperties.java
  └── TransportConfiguration.java

platform-transport/src/test/java/com/performance/platform/transport/config/
  └── TransportConfigurationTest.java — binding properties
```

## Interfaces à Implémenter

```java
// ⚠️ NE PAS mettre dans platform-domain — concept infrastructure/config
// TransportType appartient ici car il nomme des technologies concrètes (Kafka, RabbitMQ…)
package com.performance.platform.transport;
public enum TransportType { SOCKET, RABBITMQ, KAFKA, HTTP, IN_MEMORY, CUSTOM }

@ConfigurationProperties(prefix = "transport.kafka")
public record KafkaTransportProperties(String bootstrapServers, String tasksTopic, String eventsTopic, String signalsTopic, String producerAcks) {}
@ConfigurationProperties(prefix = "transport.rabbitmq")
public record RabbitMQTransportProperties(String host, int port, String virtualHost, String tasksExchange, String eventsExchange, String signalsExchange, String username, String password) {}
@ConfigurationProperties(prefix = "transport.http")
public record HttpTransportProperties(String broadcastMode, int requestTimeoutSeconds, int taskAvailabilityTimeoutSeconds, String callbackBasePath) {}
@ConfigurationProperties(prefix = "transport.socket")
public record SocketTransportProperties(String orchestratorHost, int orchestratorPort, int backlog, boolean keepAlive, int reconnectIntervalMs) {}
```

## Règles Spécifiques

- AUCUNE property pour gRPC (non implémenté).
- `TransportConfiguration` : `@Bean` `@ConditionalOnProperty(transport.type=...)` par transport.
- `TransportType` n'appartient PAS à `platform-domain` (noms de technologies = infrastructure).

## Critères de Done

- [ ] `mvn test -pl platform-transport -q` → 0 erreur
- [ ] Binding des properties testé
- [ ] `.claude/progress.md` mis à jour : ISSUE-028 → DONE
