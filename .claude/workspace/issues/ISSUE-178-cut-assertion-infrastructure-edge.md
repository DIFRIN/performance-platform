# ISSUE-178 — Cut assertion → infrastructure Edge (HttpTargetResolver port)

**PDR** : PDR-048
**Module** : `platform-use-cases`, `platform-assertion`, `platform-infrastructure`
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : —
**Estime** : L (3-6h)

---

## Objectif

Supprimer l'arête `assertion → infrastructure` (A3) : `HttpMockAssertionExecutor`
dépend de `HttpTargetRegistry`. Extraire un port `HttpTargetResolver` dans
`platform-use-cases`, faire implémenter ce port par `HttpTargetRegistry`
(infrastructure), et découpler l'executor d'assertion.

> ADR requise (ADR-036) : relocalisation du registry HTTP hors de la dépendance compile d'assertion.

## Fichiers à Modifier

```
core/platform-use-cases/src/main/java/com/performance/platform/application/ports/out/
  └── HttpTargetResolver.java              — NOUVEAU

infra/platform-infrastructure/src/main/java/com/performance/platform/infrastructure/executor/http/
  └── HttpTargetRegistry.java              — implémenter HttpTargetResolver

infra/platform-assertion-adapters/src/main/java/com/performance/platform/assertion/httpmock/
  └── HttpMockAssertionExecutor.java       — dépendre de HttpTargetResolver (plus de HttpTargetRegistry)

infra/platform-assertion-adapters/pom.xml                 — retirer la dépendance platform-infrastructure
```

## Interfaces à Implémenter

```java
package com.performance.platform.application.ports.out;
public interface HttpTargetResolver {
    HttpTarget resolve(String targetName);
}
```

## Règles Spécifiques

- `HttpTarget` reste un type du domaine/application (pas d'import infrastructure dans assertion).
- Le bean `HttpTargetResolver` est fourni par infrastructure via la composition root (platform-bootstrap).
- Aucun changement de comportement de résolution HTTP.

## Critères de Done

- [ ] `HttpMockAssertionExecutor` ne dépend plus de `platform-infrastructure`
- [ ] `infra/platform-assertion-adapters/pom.xml` ne référence plus `platform-infrastructure`
- [ ] `mvn test -pl :platform-assertion,:platform-infrastructure,:platform-use-cases -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-178 → DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (HttpTargetResolver)
