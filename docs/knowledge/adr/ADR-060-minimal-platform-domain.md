# ADR-060 - Minimal platform domain

**Status** : ACCEPTED (2026-11-15) — implémenté (2026-11-16)

## Context

`core/platform-domain` holds about 95 classes in 11 packages: `agent`,
`assertion`, `event`, `execution`, `id`, `injection`, `report`, `schedule`,
`task`, `scenario`, `template`. Everything depends on this module, so every
type placed in it is visible to every other module and every change rebuilds
the whole reactor. Several packages serve one feature only (reporting,
scheduling, templating, load injection) while `execution/` is meant to stay
generic (ADR-045).

## Decision

- `platform-domain` contains only what the generic execution model needs:
  `scenario`, `execution`, `task`, `agent`, `event`, `id`.
- Feature-specific types move next to the module that owns the feature:
  - `report` → `platform-report-api`;
  - `assertion`, `injection` → the `performance/` contracts that use them;
  - `schedule` → the scheduling use cases (ADR-048);
  - `template` → the module that resolves dynamic values (ADR-039).
- Admission rule: a type enters `platform-domain` only if at least two modules
  of different top-level folders (`execution/`, `performance/`, `infra/`,
  `bootstrap/`) need it and none of them can own it.
- The rule "0 Spring / JPA / Jackson annotation" is unchanged and applies to
  every module receiving moved types.
- The exact list of moved classes is established at implementation time, one
  package per change, with the bytecode boundary test (ADR-035) extended to
  forbid `execution/` from seeing performance-specific types.

## Consequences

The generic engine no longer sees reporting, assertion or load-model types, so
ADR-045 is enforced by the module graph and not only by a test. Changes to a
feature's model stop rebuilding unrelated modules. Types referenced from
`ScenarioDefinition` (for example `ReportDefinition`) need an opaque or
generic representation in the domain; this is the main design cost and may
block the move of a package, in which case that package stays and the reason is
recorded here. Package names of public types change: plugins compiled against
the old packages must be rebuilt.

## Note d'implémentation (2026-11-15)

Déplacés vers `platform-use-cases` (imports, tests et ports mis à jour) :
`schedule` → `com.performance.platform.application.schedule`,
`template` → `com.performance.platform.application.template`, puis (2026-11-16,
ADR-057) → `com.performance.platform.plugin.template` dans `platform-plugin-api`,
le module qui sert tous les executors résolvant des valeurs dynamiques.

Restent dans `platform-domain`, avec la raison enregistrée comme le prévoit la
décision :
- `report` : `ScenarioDefinition.report` et `ScenarioFinished.verdict` exposent
  `ReportDefinition`/`Verdict` ; les déplacer vers `platform-report-api`
  inverserait la dépendance `platform-domain` → `platform-report-api` ou
  imposerait une représentation opaque du rapport dans le domaine.
- `assertion` et `injection` : `platform-report-api` (module `core/`) les
  consomme (`AssertionSummary`, `InjectionResult`) ; les déplacer dans
  `performance/` créerait une dépendance `core/` → `performance/`.
La règle d'admission (deux modules de dossiers de tête différents) et la règle
« 0 annotation Spring/JPA/Jackson » restent applicables aux paquets déplacés.

Clôture (2026-11-16) : la décision est entièrement appliquée. Chaque paquet a
été déplacé, ou conservé avec la raison enregistrée ci-dessus, comme la décision
le prévoit. Déplacer `report`, `assertion` ou `injection` demanderait une
nouvelle ADR (représentation opaque du rapport dans le domaine).
