# ADR-036 — Port `HttpTargetResolver` pour couper assertion → infrastructure

**Date** : 2026-10-19
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (A3)
**Raffine** : PDR-048, PDR-022 (named HTTP targets)
**Impacte interfaces publiques** : OUI (additif) — `HttpTarget`, `HttpTargetResolver`

---

## Contexte

`HttpMockAssertionExecutor` (platform-assertion) dependait de
`HttpTargetRegistry` (platform-infrastructure) : un module fournisseur
d'assertions dépendait d'un adapter. C'était la seule arête
`assertion → infrastructure`.

## Décision

- Ajouter dans `platform-application.ports.out` :
  - `HttpTarget` — record framework-free (baseUrl, timeouts, headers, paths) ;
  - `HttpTargetResolver` — port `HttpTarget resolve(String targetName)`.
- `HttpTargetRegistry` implémente le port (`resolve` mappe
  `HttpTargetProperties` → `HttpTarget`).
- `HttpMockAssertionExecutor` dépend de `HttpTargetResolver` et construit son
  propre `RestClient` à partir de `HttpTarget`.
- Retirer `platform-infrastructure` du scope **compile** de
  `platform-assertion`. Le scope **test** est conservé pour les tests qui
  vérifient l'intégration avec `DefaultTaskExecutorRegistry`.
- `ModuleDependencyTest` interdit désormais toute arête
  `assertion → infrastructure` (plus d'exception `HttpMockAssertionExecutor`).

## Conséquences

- Le code principal de `platform-assertion` ne dépend plus d'aucun adapter.
- La construction du client HTTP est dupliquée (registre et assertion) mais
  limitée à un petit helper ; le port reste framework-free.
- Le test-scope infra est un choix explicite : les tests d'un module peuvent
  utiliser un adapter, le code de production non.

## Preuves

- `ModuleDependencyTest.assertionMustNotDependOnInfrastructure` vert.
- `mvn test -pl platform-application,platform-infrastructure,platform-assertion,platform-app`.
