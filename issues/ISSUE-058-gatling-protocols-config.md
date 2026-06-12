# ISSUE-058 — Support multi-protocoles Gatling (HTTP/WS/Kafka/JMS)

**PDR** : PDR-013
**Module** : `platform-injection-gatling`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-057
**Estime** : M

---

## Objectif

Configurer les dépendances et le support des protocoles Gatling (HTTP/HTTPS, WebSocket, Kafka,
JMS). NOTE : gRPC NON inclus (cohérent avec la suppression de gRPC).

## Fichiers à Créer / Modifier

```
platform-injection-gatling/pom.xml — ajouter gatling-http, gatling-kafka, gatling-jms (PAS gatling-grpc)
platform-injection-gatling/src/main/java/com/performance/platform/injection/gatling/protocol/
  └── ProtocolSupportInfo.java   — déclare les protocoles supportés

platform-injection-gatling/src/test/java/com/performance/platform/injection/gatling/protocol/
  └── ProtocolSupportInfoTest.java
```

## Règles Spécifiques

- Protocoles supportés : HTTP/HTTPS, WebSocket, Kafka, JMS.
- gRPC NON supporté (pas de `gatling-grpc`) — cohérence avec la décision de suppression de gRPC.
- Chaque dépendance Maven justifiée par un commentaire dans le pom (CC-03).

## Critères de Done

- [ ] `mvn test -pl platform-injection-gatling -q` → 0 erreur
- [ ] `ProtocolSupportInfo` ne déclare PAS gRPC
- [ ] Le pom ne contient pas `gatling-grpc`
- [ ] `progress.md` mis à jour : ISSUE-058 → DONE
