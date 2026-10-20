# PDR-048 — Cross-Module Boundary Enforcement

**Module Maven** : `platform-app` (ArchUnit), `platform-execution-engine`, `platform-assertion`, `platform-infrastructure`, `platform-reporting`, `platform-application`
**Package** : `com.performance.platform.app.arch` (test)
**Statut** : WAITING
**Specs de référence** : `improvements.md` A3, A5, T1 ; `ADR-007-plugin-jar-system.md` ; `ADR-026-assembly-strategy-thin-composition-root.md`
**Dépend de** : PDR-036/037 (issues de finalisation migration assertion)
**Issues** : ISSUE-177, ISSUE-178, ISSUE-179, ISSUE-180

---

## Responsabilité

Couper les arêtes de compilation qui brisent l'architecture hexagonale (A3) et
installer un test ArchUnit **inter-modules** qui les rend récurrentes (T1).
Les couplages réels (après correction de la review) :

1. `execution-engine → agent-runtime` : `LocalLifecycleDispatcher` importe `LocalAgent`.
2. `assertion → infrastructure` : `HttpMockAssertionExecutor` dépend de `HttpTargetRegistry`.
3. `reporting → injection-gatling` : dépendance pom (aucun import main — reporting
   utilise `domain.injection`), à supprimer du pom.

---

## Interfaces Publiques

```java
// platform-application — port extrait pour casser assertion → infrastructure
package com.performance.platform.application.ports.out;

public interface HttpTargetResolver {
    HttpTarget resolve(String targetName);   // HttpTarget = record (url, ...) existant déplacé/enveloppé
}
```

`HttpTargetRegistry` (platform-infrastructure) implémente `HttpTargetResolver`.
`HttpMockAssertionExecutor` (platform-assertion) ne dépend plus que du port.

`LocalLifecycleDispatcher` (engine) dépend du port
`application.ports.out.LifecycleSignalHandler`, implémenté par `LocalAgent`.
L'engine ne connaît plus `platform-agent-runtime`.

---

## Règles de Comportement

- `platform-execution-engine` ne doit importer AUCUN type de `platform-agent-runtime`
  ni `platform-assertion` ni `platform-infrastructure` (main ET test).
- `platform-reporting` ne doit pas dépendre de `platform-injection-gatling` (pom retiré).
- `ModuleDependencyTest` (T1) encode le DAG cible et échoue si une arête interdite
  réapparaît. Il vit dans `platform-app` (seul module qui voit tout le graphe Maven).

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-004 (ports application), PDR-010/022 (infrastructure HttpTargetRegistry),
  PDR-014 (assertion HttpMockAssertionExecutor), PDR-037 (TaskExecutorLookup)

Ce PDR est utilisé par :
  PDR-049 (Modulith decision — T1 remplace la vérification décorative)
  PDR-050 (consolidation — gate)
```

---

## Critères de Done (PDR complet)

- [ ] `LocalLifecycleDispatcher` déplacé hors engine (ISSUE-177)
- [ ] `HttpTargetResolver` port + `HttpMockAssertionExecutor` découplé (ISSUE-178)
- [ ] pom `reporting → injection-gatling` supprimé (ISSUE-179)
- [ ] `ModuleDependencyTest` vert (ISSUE-180)
- [ ] `mvn test -pl platform-app -q` → 0 erreur
