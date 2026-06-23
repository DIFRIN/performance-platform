# ADR-020 — Read-model des exécutions : findAll/deleteById, ExecutionProgress, et calcul de progression

**Date** : 2026-06-23
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : PDR-027 (ISSUE-119/120/121) étend `ExecutionRepository` avec `findAll(int)` /
`deleteById(ExecutionId)`, ajoute le record `ExecutionProgress` dans `platform-domain`, et un
`ExecutionProgressCalculator` qui « dérive la progression depuis un `ExecutionState` ». La revue
a identifié deux problèmes : (1) `ExecutionState` ne contient PAS les résultats par task, donc le
calcul de progression à partir du seul `ExecutionState` est impossible ; (2) la question CQRS de
savoir s'il faut un port de query séparé.

---

## Contexte

`ExecutionState` (domaine) expose : `id`, `scenarioId`, `status`, `phaseStatuses`
(`Map<Phase, PhaseStatus>`), `context`, `startedAt`, `updatedAt`. Les résultats par task ne sont
PAS dans `ExecutionState` : ils sont stockés séparément et lus via
`ExecutionRepository.getTaskResults(id, taskId)`. Donc `{total, ok, ko, running}` au niveau task
ne peut PAS être dérivé d'un `ExecutionState` seul.

Par ailleurs `ExecutionRepository` est un port sortant unique (commandes + requêtes mélangées).
PDR-027 y ajoute deux méthodes de lecture/suppression.

## Décision

**1. `ExecutionProgress` reste dans `platform-domain`** comme value object pur immuable
(0 annotation framework, validation des compteurs ≥ 0). C'est un read-model de domaine légitime,
au même titre que `PhaseStatus`/`ExecutionStatus`. Le **calcul** reste hors domaine (application).

**2. Le calcul de progression NE prend PAS un `ExecutionState` seul en entrée.**
`ExecutionProgressCalculator` doit recevoir l'information par-task nécessaire. Source canonique :
les `TaskResult` agrégés. La signature corrigée est :

```java
// platform-application
ExecutionProgress calculate(ExecutionState state, Map<TaskId, Map<AgentId, TaskResult>> taskResults);
```

`total` = nombre de tasks planifiées (depuis le plan/contexte de `state`) ; `ok`/`ko` dérivés du
`TaskStatus` réel des `TaskResult` ; `running` = `total - terminées` (tasks planifiées sans
résultat terminal), JAMAIS un calcul naïf sur des compteurs non corrélés. Le use case
(`ListExecutionsService` / status) est responsable de fournir les `taskResults` via le repository.

**3. PAS de port de query séparé en v1.** `findAll(int)` et `deleteById(ExecutionId)` restent sur
`ExecutionRepository`. La séparation CQRS (ExecutionQueryRepository) n'apporte pas de bénéfice
tangible ici (même store JPA, pas de modèle de lecture distinct, pas de scaling read/write séparé
prévu). On préfère la cohésion du port unique. Si un besoin de projection/read-store dédié émerge,
un ADR ultérieur introduira un port de query.

**4. `deleteById` est interdit sur une exécution active.** Supprimer une exécution
`STARTED`/`RUNNING` casserait les invariants (résultats arrivant en cours, checkpointing CNF-02).
La règle : `DeleteExecutionUseCase.delete(id)` lève une exception applicative
(`ExecutionNotDeletableException` → HTTP 409 Conflict) si le statut est `STARTED` ou `RUNNING`.
Suppression autorisée uniquement pour `COMPLETED | FAILED | CANCELLED`. L'IHM doit donc `cancel`
avant `delete`. `deleteById` au niveau repository reste no-op si l'id est absent.

## Justification

- Dériver la progression d'un `ExecutionState` seul est techniquement impossible (les résultats
  par task n'y sont pas) — laisser la signature initiale aurait produit un calculateur faux ou un
  hack lisant un champ inexistant.
- Le port unique évite une sur-ingénierie CQRS non justifiée par les contraintes actuelles.
- Interdire la suppression d'une exécution active protège les invariants de checkpointing et évite
  les courses entre suppression et arrivée de résultats (multi-claim ADR-011).

## Conséquences

**Positives** :
- Calcul de progression correct et testable (ok/ko/running dérivés des `TaskResult` réels).
- Pas de port superflu ; domaine pur préservé.
- Suppression sûre vis-à-vis du lifecycle d'exécution.

**Négatives / Contraintes** :
- Le listing (`findAll`) suivi d'un calcul de progression par exécution peut entraîner N+1 lectures
  de `taskResults`. En v1 borné par `limit` (défaut 50) c'est acceptable. À optimiser (projection
  d'agrégat persistée) si le volume l'exige — hors scope v1.
- L'IHM doit gérer le 409 sur delete d'une exécution active (afficher « annuler d'abord »).

**Fichiers impactés** :
- ISSUE-120 : corriger la signature de `ExecutionProgressCalculator` (ajouter `taskResults`) ;
  ajouter `ExecutionNotDeletableException` + règle de statut dans `DeleteExecutionService`.
- ISSUE-121 : `DELETE /executions/{id}` → 409 si active (en plus de 204 si terminée) ; le calcul
  de progress passe les `taskResults` au calculateur.
- ISSUE-119 : préciser que `deleteById` est no-op si absent (déjà ok) ; pas de garde de statut au
  niveau repository (la garde est applicative).
- PDR-027 : aligner « Règles de Comportement » (progression + delete actif).

## Alternatives Rejetées

| Alternative | Raison du rejet |
|---|---|
| `ExecutionProgress` en `platform-application` (DTO) | Record pur sans framework ; cohérent comme VO domaine read-model |
| Port `ExecutionQueryRepository` séparé (CQRS) | Pas de read-store distinct ni de besoin de scaling séparé en v1 |
| `delete` autorisé sur exécution active (cancel implicite) | Mélange deux intentions ; risque de course avec résultats entrants ; 409 explicite plus sûr |
| Calculer la progression dans le controller | Logique métier hors couche application ; viole la règle « jamais côté controller » du PDR |
