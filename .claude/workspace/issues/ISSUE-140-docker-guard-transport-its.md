# ISSUE-140 — Garde Docker sur les ITs de platform-transport

**PDR** : PDR-032
**Module** : `platform-transport`
**Statut** : APPROVED
**Priorité** : P1 (critique)
**Bloquée par** : —
**Estime** : S (< 1h)

---

## Objectif

Ajouter `@Testcontainers(disabledWithoutDocker = true)` aux 2 ITs Testcontainers de
`platform-transport` (Kafka, RabbitMQ) pour qu'ils soient **skipped** sans Docker (ADR-023).

## Fichiers à Créer / Modifier

```
MODIFIER (tests) :
platform-transport/src/test/java/.../KafkaExecutionTransportIT.java
platform-transport/src/test/java/.../RabbitMQExecutionTransportIT.java
  — remplacer @Testcontainers par @Testcontainers(disabledWithoutDocker = true)
```

## Interfaces à Implémenter

```java
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class KafkaExecutionTransportIT { /* @Container ... inchangé */ }
```

## Règles Spécifiques

- Modifier uniquement l'annotation `@Testcontainers`. Logique de test, `@Container`, assertions
  inchangés.
- Pas de `Assumptions.assumeTrue(...)`.

## Critères de Done

- [ ] `mvn test -pl platform-transport -q` (sans Docker) → vert, les 2 IT `skipped`
- [ ] `mvn verify -pl platform-transport -P integration-tests` (avec Docker) → les 2 IT s'exécutent
- [ ] Les 2 classes portent `@Testcontainers(disabledWithoutDocker = true)`
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
</content>
