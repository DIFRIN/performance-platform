# ISSUE-076 — Logging JSON structuré + ObservabilityConfiguration

**PDR** : PDR-017
**Module** : `platform-observability`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-074
**Estime** : S

---

## Objectif

Configurer le logging JSON structuré (MDC : executionId/scenarioId/taskId/agentId/phase) et la
classe `ObservabilityConfiguration` (MeterRegistry, tracer OTel).

## Fichiers à Créer

```
platform-observability/src/main/java/com/performance/platform/observability/config/
  ├── ObservabilityConfiguration.java
  └── ExecutionContextMdcFilter.java   — pose le MDC
platform-observability/src/main/resources/
  └── logback-spring.xml               — encoder JSON

platform-observability/src/test/java/com/performance/platform/observability/config/
  └── ExecutionContextMdcFilterTest.java
```

## Interfaces à Implémenter

```java
@Configuration
public class ObservabilityConfiguration { /* MeterRegistry customizations, OTel tracer */ }

@Component
public class ExecutionContextMdcFilter { /* met/retire les clés MDC */ }
```

## Règles Spécifiques

- Logs JSON avec contexte : `executionId, scenarioId, taskId, agentId, phase` (CNF-04).
- MDC posé/retiré proprement (try/finally) — pas de fuite entre threads.
- Format de log conforme : `action={} executionId={} taskId={}`.

## Critères de Done

- [ ] `mvn test -pl platform-observability -q` → 0 erreur
- [ ] Le MDC contient les clés attendues pendant le traitement
- [ ] `.claude/progress.md` mis à jour : ISSUE-076 → DONE
- [ ] `.claude/context/interfaces-registry.md` mis à jour
