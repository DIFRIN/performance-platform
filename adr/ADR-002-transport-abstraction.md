# ADR-002 — Abstraction du Transport (ExecutionTransport)

**Date** : 2026-06-05  
**Statut** : ACCEPTED  
**Décideurs** : Équipe Architecture

---

## Contexte

La plateforme doit pouvoir fonctionner dans des environnements où différents
systèmes de messagerie sont disponibles (environnement cloud AWS = Kafka,
on-premise = RabbitMQ, dev local = Socket).

## Décision

Interface `ExecutionTransport` avec sélection uniquement par configuration.
4 implémentations : SOCKET, RABBITMQ, KAFKA, GRPC.

## Justification

- Zéro code conditionnel sur le type de transport dans la logique métier.
- `@ConditionalOnProperty` Spring garantit qu'une seule implémentation est active.
- Tests unitaires peuvent utiliser un `InMemoryExecutionTransport` de test.
- Migration d'infrastructure sans recompilation.

## Conséquences

- Tout message doit être sérialisable en JSON (Jackson).
- L'idempotence côté récepteur est obligatoire (clé : `MessageId`).
- La garantie de livraison varie selon le transport (documenté dans spec 05).

## Alternatives Rejetées

- **gRPC uniquement** : coupling fort, génération de code, complexité mTLS obligatoire dès le dev.
- **Spring Cloud Stream** : abstraction utile mais ajoute une couche qui masque les comportements
  spécifiques des brokers, ce qui est problématique pour une plateforme de performance.
