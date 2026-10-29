# ADR-031 — ExecutionContext : outputs nommes et store persistant

**Date** : 2026-10-20
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (B1)
**Raffine** : PDR-001 (ExecutionContext/TaskResult)
**Impacte interfaces publiques** : OUI — ajout de `getOutput`/`getFirstOutput`, suppression de `get(Class<T>)`/`getFirst(Class<T>)`

---

## Contexte

`ExecutionContext.with()` deep-copiait tout le store imbrique a chaque
resultat (O(store), donc O(N²) sur N resultats). Les getters
`get(taskId, agentId, Class<T>)` et `getFirst(taskId, Class<T>)` retournaient
la premiere valeur castable : avec deux outputs `String`, le resultat etait
indefini. `Class<T>` servait de cle alors qu'il n'en est pas une.

## Décision

### Outputs nommes

- Ajout de :
  - `getOutput(taskId, agentId, outputName, type)` ;
  - `getFirstOutput(taskId, outputName, type)` (iteration deterministe par
    `agentId`).
- Les cles de sortie deviennent contractuelles :
  - `InjectionResult.OUTPUT_KEY = "result"` ;
  - `AssertionSummary.OUTPUT_KEY = "assertion"`.
- Suppression de `get(Class<T>)`/`getFirst(Class<T>)` de `ExecutionContext`.
- `PartialExecutionContext` conserve ses accesseurs historiques : son store
  aplati (`taskId → agentId → Object`) ne porte pas de nom d'output ; il est
  transmis aux agents via le transport. Une evolution du format partiel
  fera l'objet d'un ADR dedie.

### Store persistant

- Le composant `store` reste declare `Map<String, Map<String, TaskResult>>`
  (API externe inchangee) mais l'implementation interne est une chaine de
  couches persistantes (`PersistentStore`).
- `with()` ajoute une couche en O(profondeur) et ne copie que la map interne
  de la task modifiee (O(agents de cette task)), jamais le store complet.
- Les tasks inchangees partagent leurs maps (teste par `assertSame`).
- Le snapshot lisible est materialise paresseusement et mis en cache par
  instance ; `store()` reste une vue non modifiable.

## Consequences

- Plus de lookup type-erasé en production : assertion et reporting utilisent
  les cles nommees.
- `with()` n'est plus O(store) ; les entrees inchangees ne sont pas copiees.
- Immuabilite externe conservee (CF-02) : aucune mutation, aucune methode
  `set`.
- Les fixtures historiques de reporting utilisant `"assertionResult"`
  restent supportees par un fallback explicite dans le report engine.

## Preuves

- `ExecutionContextNamedOutputTest` : cles nommees, determinisme 1 000 runs,
  partage des maps inchangees, store non modifiable.
- `mvn test -pl :platform-domain,:platform-plugin-api,:platform-use-cases,
  platform-injection-gatling,platform-assertion,platform-execution-engine,
  platform-agent-runtime,platform-reporting,platform-bootstrap` vert.
- ITs `LocalDistributedEquivalenceIT`, `LocalFlowIT`, `LocalModeAllTasksIT`,
  `WebUiApiIT` verts.
