# ADR-026 — Stratégie d'assemblage : composition root mince, self-wiring par module, suppression des ports redondants

**Date** : 2026-06-29
**Statut** : ACCEPTED
**Décideurs** : Architect + utilisateur (décisions D1, D2 validées le 2026-06-29)
**Raffine** : ADR-025 (assemblage runtime + beans conditionnels) — précise *où* vit chaque bean
et *quelles abstractions* disparaissent.
**Impacte interfaces publiques** : suppression du port `ExecutionEngine` (CC-04 — couvert par ce
ADR).

---

## Contexte

L'audit de l'assemblage `platform-app` / modules a révélé trois smells (cf. évaluation
Architect 2026-06-29) :

- **S1 — Ports redondants** : `ExecutionEngine.execute/cancel` (module engine) duplique
  `ExecuteScenarioUseCase.execute` / `CancelExecutionUseCase.cancel` (ports in application) —
  **signatures identiques**. Idem `TaskExecutorLookup` (engine) ⟂ `TaskExecutorRegistry` (infra).
  Cette redondance force des `@Bean` lambdas d'adaptation dans la racine de composition.
- **S2 — Politique d'annotation incohérente** : certains modules s'auto-câblent (`@Service`/
  `@Component`), d'autres (framework-free) exigent un câblage explicite, sans règle claire →
  des beans manquent (personne ne « possède » leur câblage).
- **S3 — Config partagée trop large** + triple source de vérité du mode (`MODE` env /
  `spring.profiles.active` / `runtime.mode`+`runtime.role`).

Bug latent associé : `DefaultAgentAvailabilityChecker` et `DefaultTaskCorrelationTracker`
(engine, orchestrateur-only) sont des `@Component` **inconditionnels** → instanciés même en
LOCAL où `AgentRegistryPort` n'existe pas → contexte LOCAL en échec.

---

## Décision

### D1 — Les engines implémentent directement les ports in ; suppression de `ExecutionEngine`

`LocalExecutionEngine` et `RemoteExecutionEngine` **implémentent** `ExecuteScenarioUseCase` et
`CancelExecutionUseCase` (signatures déjà identiques). Le port **`ExecutionEngine` est
supprimé** (abstraction redondante sans valeur de découplage : aucun appelant hors couche use
case). Les engines restent `@Service` `@ConditionalOnProperty(runtime.mode=LOCAL|DISTRIBUTED)`
→ exactement **un** bean de chaque port in par mode ; les contrôleurs/CLI injectent les ports
in, sans aucune colle dans `platform-app`.

`GetExecutionStatusUseCase` n'est **pas** porté par l'engine : c'est une **query read-model**
implémentée par un service framework-free `GetExecutionStatusService(ExecutionRepository)` dans
`platform-application` (cohérent avec `ListExecutionsService`/`DeleteExecutionService`
existants), câblé par la racine. Sépare commande (engine) et lecture (read-model).

### D2 — `platform-scenario-dsl` reste framework-free

`YamlScenarioParser`, `DefaultScenarioValidator`, `DefaultScenarioParsingService` conservent
**0 annotation Spring**. Leur câblage (`ScenarioParsingUseCase` bean) vit dans la **racine de
composition** `platform-app`, seul endroit légitime pour assembler des modules framework-free.

### Règle d'annotation binaire (lève S2)

| Catégorie | Modules | Spring ? | Câblage |
|---|---|---|---|
| **Cœur framework-free** | `platform-domain`, `platform-application`, `platform-scenario-dsl`, `platform-plugin-api` | **0 annotation** (inviolable) | par la racine `platform-app` |
| **Modules adapter/service** | `execution-engine`, `transport`, `infrastructure`, `agent-runtime`, `injection-gatling`, `assertion`, `reporting`, `observability` | stéréotypes Spring autorisés | **self-wiring** : chaque module possède sa `@Configuration` conditionnelle sur SES propriétés |
| **Composition root** | `platform-app` | oui (mince) | entry point, résolution du mode, web/sécurité/CLI, câblage du cœur framework-free, et l'unique glue cross-module |

### Possession du câblage par le module (lève S2/S3)

- **`ExecutionConfig`** : possédé par `platform-execution-engine` via `@ConfigurationProperties`
  + `@Bean` `@ConditionalOnProperty(runtime.mode=DISTRIBUTED)` (seul `RemoteExecutionEngine` en a
  besoin). Le record `ExecutionConfig` reste framework-free dans `platform-application` ; c'est
  le *binding* qui vit dans l'engine.
- **`AgentRegistry`** (`InMemoryAgentRegistry`, qui IS-A `AgentRegistryPort`) : possédé par
  `platform-agent-runtime` via une `@Configuration` `@ConditionalOnExpression(DISTRIBUTED &&
  ORCHESTRATOR)`. Un seul bean satisfait `AgentRegistry` et `AgentRegistryPort`.
- **Beans orchestrateur-only de l'engine** (`DefaultAgentAvailabilityChecker`,
  `DefaultTaskCorrelationTracker`) : `@ConditionalOnProperty(runtime.mode=DISTRIBUTED)` (corrige
  le bug LOCAL).
- **datasource / JPA / repository** : conditions possédées par `platform-infrastructure` (ADR-025).

### L'unique glue légitime dans la racine

`TaskExecutorLookup` (port de l'engine) → adapter `RegistryTaskExecutorLookup` qui combine
`TaskExecutorRegistry` (infrastructure) **et** `AssertionExecutorRegistry` (assertion). Comme
`platform-app` est le **seul** module dépendant à la fois de l'engine, de l'infrastructure et de
l'assertion, cet adapter vit dans `platform-app` — exception assumée et documentée (glue
cross-module irréductible).

### Surface finale de `platform-app` (composition root mince)

Beans restants : `RegistryTaskExecutorLookup` (glue), `ScenarioParsingUseCase`(+parser+validator,
câblage framework-free), `GetExecutionStatusUseCase` (câblage du read-model framework-free) +
l'existant (web/sécurité/CLI/runtime-mode, `ExecutionUseCaseConfiguration`, `AgentRuntimeConfiguration`).
**Plus aucun `@Bean` d'adaptation execute/cancel, plus de `ExecutionConfig`/`AgentRegistry`
construits ici.**

### Config (atténue S3)

- **Une source de vérité du mode** : `runtime.mode` + `runtime.role` (avec override `MODE` env,
  ADR-006). Réduire la dépendance aux profils Spring à l'activation des yaml.
- **Config propre au module** : chaque `@Configuration` lit son préfixe ; `application.yaml`
  commun réduit au transverse (management/logging/sécurité on-off). `@ConditionalOnProperty` /
  `@ConditionalOnBean` / `@ConditionalOnExpression` uniquement (jamais `@Profile`, CF-03).

---

## Justification

- **Rôles distincts** : règle d'annotation binaire sans ambiguïté ; chaque module possède son
  câblage ; la racine ne fait que de la composition irréductible.
- **Moins de colle** : suppression du port `ExecutionEngine` ⇒ zéro `@Bean` d'adaptation
  execute/cancel ; CQRS-lean pour le statut.
- **Moins de config partagée** : config possédée par le module consommateur ; une source de
  vérité du mode.
- **Correction de bug** : les beans orchestrateur-only ne polluent plus LOCAL.

---

## Conséquences

**Positives** : `platform-app` mince et stable ; modules autoportants/testables ; démarrage
propre dans les 3 modes ; E2E pourront cesser de reconstruire l'app à la main.

**Négatives / Contraintes** :
- Suppression du port public `ExecutionEngine` (breaking interne) — acté par ce ADR (CC-04) ;
  mettre à jour les tests engine qui référençaient `ExecutionEngine`.
- Les modules adapter doivent dépendre de `spring-boot-autoconfigure` (déjà le cas pour la
  plupart).
- `ExecutionConfig` binding dans l'engine ; `AgentRegistry` config dans agent-runtime (légère
  migration de responsabilité depuis platform-app).
- Ne **pas** sur-ingénier en `@AutoConfiguration`/`spring.factories` : Modulith + component-scan
  + `@Configuration` par module suffisent.

**Fichiers impactés (indicatif)** :
- `platform-execution-engine` : engines `implements ExecuteScenarioUseCase, CancelExecutionUseCase` ;
  suppression `ExecutionEngine.java` ; `ExecutionEngineConfiguration` (+`ExecutionEngineProperties`)
  pour `ExecutionConfig` ; conditions DISTRIBUTED sur availability checker + correlation tracker.
- `platform-application` : ajout `GetExecutionStatusService` (framework-free).
- `platform-agent-runtime` : `@Configuration` conditionnelle exposant `AgentRegistry`.
- `platform-app` : `RuntimeAssemblyConfiguration` réduite (lookup glue + scenario parsing +
  status read-model wiring) ; smoke tests par mode.
- `platform-infrastructure` : datasource/JPA/repository conditionnels (ADR-025, inchangé).

---

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| Garder `ExecutionEngine` + un adapter mince (D1 bis) | Conserve une abstraction redondante et de la colle ; l'utilisateur a tranché pour la suppression. |
| Faire de `scenario-dsl` un module-adapter Spring (D2 bis) | L'utilisateur a tranché : `scenario-dsl` reste framework-free. |
| Tout câbler dans `platform-app` (statu quo PDR-033 v1) | Concentre la colle, brouille les rôles, config partagée large. |
| Engine implémente aussi `GetExecutionStatusUseCase` | Mélange commande/lecture dans une classe déjà riche ; un read-model dédié est plus propre. |
| `@AutoConfiguration` + `spring.factories` par module | Sur-ingénierie ici ; component-scan + `@Configuration` conditionnelle suffit. |
</content>
