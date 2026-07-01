# ADR-027 — Distributed Assertion Lifecycle: General-Purpose Task Signals

**Date** : 2026-06-30
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : Le System Designer a produit un design complet pour les assertions distribuees (PDR-034, 035, 036, 037, 038 + ISSUE-148 a 159). Ce ADR valide la coherence architecturale de l'ensemble et documente les decisions structurelles.

---

## Contexte

La plateforme doit supporter les assertions distribuees dans le meme modele d'execution que les taches de preparation et d'injection. Le design devait repondre a 8 deep-dive questions, dont les reponses ont ete consolidees dans `.claude/workspace/assertion-distributed-analysis.md`.

Les decisions couvertes par ce ADR sont :
1. L'unification du contrat `AssertionExecutor` avec `TaskExecutor`
2. L'introduction d'un signal de cycle de vie generaliste (`ExecutionLifecycleSignal`)
3. Le format de resultat unifie (`AssertionSummary` dans `TaskResult.outputs`)
4. Le parametrage YAML (`linkedTo`, `stopBehavior`, `gracePeriodDuration`)
5. La strategie de deploiement en deux phases (Phase A point-in-time, Phase B interval)

---

## Decision 1 — `AssertionExecutor extends TaskExecutor` (Option A)

L'interface `AssertionExecutor` etend desormais `TaskExecutor` avec une implementation par defaut de `execute()` qui appelle `evaluate()` et convertit l'`AssertionResult` en `TaskResult`.

**Justification** :
- Zero breaking change pour les plugins externes (ADR-007) -- `evaluate()` et `getSupportedAssertionName()` restent inchangees
- Zero changement cote agent -- `TaskExecutionPipeline` resout deja les `TaskExecutor` par nom
- Elimination du double registre (`TaskExecutorRegistry` + `AssertionExecutorRegistry`) et du double chemin de lookup
- La conversion `AssertionResult` -> `TaskResult` est dans la default method de l'interface, pas dupliquee dans 6+ executors
- Les plugins compiles contre l'ancien `AssertionExecutor` continuent de fonctionner (interface additive)

**Alternative rejetee** : `ExecutionCapability` unifie (Option C) -- touchait ~35 fichiers, cassait tous les plugins externes, cout de migration disproportionne.

---

## Decision 2 — `ExecutionLifecycleSignal` generaliste (START/STOP)

Un nouveau type de signal, `ExecutionLifecycleSignal` implementant `AgentSignal`, est envoye par l'engine pour TOUTES les taches (pas seulement les assertions). Il porte une `LifecycleAction` (START ou STOP) et une `Map<String, Object> parameters` pour les parametres specifiques.

**Justification** :
- Generaliste -- applicable a toutes les tasks, pas seulement les assertions
- Reutilise l'infrastructure de transport existante via `AgentSignal` sealed hierarchy
- Zero modification des 5 implementations de transport (Kafka, RabbitMQ, HTTP, Socket, InMemory)
- Permet de futures fonctionnalites (progress tracking, cancellation, distributed tracing) sans redesign
- Remplace la conception precedente `PhaseSignal` + `ASSERTION_INTERVAL` phase, qui etait plus complexe et specifique aux assertions

**Alternative rejetee** : Champ `lifecycle` sur `TaskExecutionRequest` -- cassait le modele 1:1 entre `TaskExecutionRequest` et `TaskResult` du `TaskCorrelationTracker`.

---

## Decision 3 — `AssertionSummary` dans `TaskResult.outputs["assertion"]`

Un record domaine `AssertionSummary` est place dans `TaskResult.outputs()` sous la cle `"assertion"`. C'est le format canonique de serialisation pour tous les resultats d'assertion, remplacant l'ancien `Evidence` pour le transport tout en le conservant pour l'usage interne des executors.

**Justification** :
- Format unique pour le reporting -- tous les types d'assertion produisent la meme structure
- Inclut l'historique (`List<AssertionSample>`) pour les assertions basees sur des intervalles (Phase B)
- Pas de nouvel enum de verdict -- reutilise `AssertionStatus` existant (PASSED, FAILED, SKIPPED, ERROR)
- `AssertionResult` et `Evidence` conserves comme format interne pour les executors

---

## Decision 4 — Parametres YAML `linkedTo`, `stopBehavior`, `gracePeriodDuration`

Ces parametres sont places dans les champs YAML du step et lus par l'engine depuis `StepDefinition.parameters()`. Ils ne sont PAS encodes dans des types du domaine Java.

**Justification** :
- `linkedTo` est structurel (reference un autre step) -- place comme champ top-level dans le YAML
- `stopBehavior` et `gracePeriodDuration` sont comportementaux -- places dans `parameters`
- L'engine lit ces parametres et les transmet dans `ExecutionLifecycleSignal.parameters`
- L'agent les recoit via le signal, pas via `step.parameters()`
- Separation claire : le YAML definit, l'engine transmet, l'agent consomme

---

## Decision 5 — Strategie en deux phases (Phase A maintenant, Phase B deferred)

**Phase A (immediate)** : Assertions point-in-time uniquement. Les assertions s'executent dans la phase ASSERTION (post-injection). `AssertionExecutor extends TaskExecutor`, `AssertionSummary` dans `TaskResult.outputs`, `ExecutionLifecycleSignal` START/STOP encadrent toutes les taches.

**Phase B (future)** : Assertions basees sur des intervalles avec `linkedTo`. Les signaux START/STOP delimitent la fenetre de monitoring concurrente avec l'injection. La boucle de sampling et les `stopBehavior` sont actives.

**Justification** :
- Reduit le risque d'implementation -- la Phase A est un changement mecanique sans nouvelle logique concurrente
- Les interfaces de la Phase A sont concues pour supporter la Phase B sans rework
- `ExecutionPlan.assertionIntervalSteps`, `ExecutionLifecycleSignal`, `AssertionSample` sont crees des la Phase A mais actives en Phase B

---

## Conséquences

**Positives** :
- Unification du pipeline d'execution -- un seul chemin pour toutes les taches
- Extension future des signaux de cycle de vie aisee (PAUSE, RESUME, etc.)
- Zero breaking change pour les plugins externes
- Transport transparent des nouveaux signaux via la hierarchie `AgentSignal`
- Elimination d'une branche conditionnelle (`if phase == ASSERTION`) dans `DagPhaseExecutor`

**Négatives / Contraintes** :
- Nouvelle dependance Maven : `platform-execution-engine` doit dependre de `platform-agent-runtime` pour `LocalAgent` (utilise par `LocalLifecycleDispatcher`)
- Collision de nommage entre `DatabaseTaskExecutor` (PREPARATION, "database") et `DatabaseAssertionExecutor` (ASSERTION, "database") -- a resoudre par renommage de l'assertion en "database-assertion" (ISSUE-156)
- La hierarchie `AgentSignal` sealed avec 2 permits -- tout `switch` exhaustif sur `AgentSignal` doit etre mis a jour (force de maniere desirable par le compilateur)
- La Phase B (assertions interval avec `linkedTo`) necessitera ses propres ADR et PDRs

**Fichiers impactés** :
- `.claude/knowledge/glossary.md` -- ajouter les nouveaux termes (`AssertionSummary`, `AssertionSample`, `ExecutionLifecycleSignal`, `LifecycleAction`, `linkedTo`, `stopBehavior`)
- `.claude/knowledge/architecture.md` -- mettre a jour la liste des events (ajouter `ExecutionLifecycleSignal` comme signal, pas comme event de domaine)
- `.claude/knowledge/specs/07-assertion-framework.md` -- mettre a jour le format de sortie des assertions
- `.claude/knowledge/specs/02-execution-engine.md` -- mettre a jour la section `ExecutionPlan` (ajout `assertionIntervalSteps`)
- `.claude/workspace/interfaces-registry.md` -- deja mis a jour par le System Designer
- `platform-execution-engine/pom.xml` -- ajouter dependance vers `platform-agent-runtime`
- `platform-domain/src/main/java/com/performance/platform/domain/event/AgentSignal.java` -- ajouter `ExecutionLifecycleSignal` a la clause `permits`

---

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| `ExecutionCapability` unifie (Option C, DQ6) | Cassure des plugins externes, ~35 fichiers touches, cout disproportionne |
| Champ `lifecycle` sur `TaskExecutionRequest` (DQ1-B) | Cassait le modele 1:1 request/result du `TaskCorrelationTracker` |
| `PhaseSignal` + `ASSERTION_INTERVAL` phase | Plus complexe, specifique aux assertions, moins generaliste |
| `AssertionVerdict` nouvel enum | `AssertionStatus` existant a les memes valeurs et la meme semantique |
| Suppression immediate de `AssertionExecutorRegistry` | Conserve deprecie pour la compatibilite ascendante du code legacy |
