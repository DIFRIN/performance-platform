# ADR-003 — Gatling comme Moteur d'Injection de Charge

**Date** : 2026-06-05  
**Statut** : ACCEPTED  
**Décideurs** : Équipe Architecture

---

## Contexte

Choix du moteur de génération de charge.

## Décision

Gatling 3.x avec Java DSL, exécuté in-process via `GatlingRunner`.

## Justification

- Gatling est l'outil de référence JVM pour les tests de performance.
- Java DSL disponible depuis Gatling 3.7 : pas de Scala requis.
- Exécution in-process : pas de processus fils, même JVM, même contexte.
- Rapport HTML natif Gatling embarqué dans le rapport de campagne.
- Support natif HTTP, WebSocket, Kafka, JMS, gRPC via plugins officiels.

## Conséquences

- Les simulations Gatling doivent être compilées et disponibles dans le classpath.
- Les load models sont traduits en `OpenInjectionStep` au runtime.
- Le répertoire de résultats Gatling est relatif au répertoire de travail.
- Limitation : Gatling utilise des threads Netty en interne — ne pas mélanger
  avec Virtual Threads sur le même pool.

## Alternatives Rejetées

- **k6** : excellent outil mais Go/JavaScript, pas JVM — intégration complexe.
- **JMeter** : XML verbose, GUI-centric, moins adapté à une plateforme code-first.
- **Locust** : Python, hors contexte JVM.
