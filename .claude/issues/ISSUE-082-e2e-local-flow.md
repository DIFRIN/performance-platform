# ISSUE-082 — Test E2E mode LOCAL (submit → exécution → rapport)

**PDR** : PDR-018
**Module** : `platform-app`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-079, ISSUE-080, ISSUE-081, ISSUE-038, ISSUE-066
**Estime** : L

---

## Objectif

Test d'intégration E2E en mode LOCAL : soumettre un scénario YAML, exécuter les 3 phases via
`LocalAgent` + transport in-memory, et vérifier la génération du rapport.

## Fichiers à Créer

```
platform-app/src/test/java/com/performance/platform/app/e2e/
  ├── LocalFlowE2ETest.java          — @SpringBootTest, profil local, Testcontainers PostgreSQL
  └── resources/scenarios/e2e-local.yaml
```

## Scénario de Test

- Profil `local` (`transport.type=IN_MEMORY`, `runtime.mode=LOCAL`).
- PostgreSQL via Testcontainers.
- Scénario : 1 step PREPARATION (filesystem), 1 step INJECTION (gatling mock/minimal), 1 step ASSERTION (file).
- Soumettre via l'API REST, attendre la fin, récupérer le rapport.

## Règles Spécifiques

- Vérifier la séquence PREPARATION → INJECTION → ASSERTION.
- Vérifier que `CampaignReport` est généré avec un `Verdict`.
- Vérifier le checkpoint en base (ExecutionState COMPLETED).

## Critères de Done

- [ ] `mvn verify -pl platform-app -P integration-tests` → E2E passe
- [ ] Le rapport est généré et contient un verdict
- [ ] `ExecutionState` persisté en COMPLETED
- [ ] `.claude/progress.md` mis à jour : ISSUE-082 → DONE
