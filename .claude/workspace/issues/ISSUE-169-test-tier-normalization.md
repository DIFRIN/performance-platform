# ISSUE-169 — Test-Tier Normalization

**PDR** : PDR-043
**Module** : root + tous modules (surefire/failsafe)
**Statut** : DONE
**Priorité** : P1
**Bloquée par** : —
**Estime** : M (1-3h)

---

## Objectif

Normaliser les paliers de tests (D2 corrigé) : `*Test` = unitaire (surefire, rapide,
déterministe, sans conteneur) ; `*IT` = intégration (failsafe, `-P integration-tests`).
Le vrai problème n'est PAS des `@Disabled` (D3 écarté) ni des `*E2ETest` qui ne
tournent pas (ils tournent déjà sur `mvn test`) — c'est l'incohérence des paliers.

## Fichiers à Modifier

```
pom.xml                                  — documenter la convention (commentaire)
platform-app/pom.xml                     — reclasser les 4 classes Testcontainers si *E2ETest → *IT
platform-transport/pom.xml               — vérifier Kafka/RabbitMQ *IT déjà derrière failsafe
platform-infrastructure/pom.xml          — vérifier DatabaseAssertionExecutorIT
```

## Règles Spécifiques

- Tout test nécessitant un conteneur (Testcontainers, Docker) DOIT être `*IT`
  (ou exécuté uniquement sous `-P integration-tests`), jamais `*Test`.
- Les `*E2ETest` in-memory (pas de conteneur) restent `*Test` (surefire).
- Ne toucher AUCUN `@DisabledOnOs(OS.WINDOWS)` — légitime (D3 écarté).

## Critères de Done

- [ ] Convention de paliers documentée dans le root pom
- [ ] `mvn test` (sans profile) reste rapide et sans conteneur
- [ ] `mvn verify -P integration-tests` exécute la suite complète
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-169 → DONE
