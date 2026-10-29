# PDR-043 — Safety Net: CI, Test Tiers, LOCAL≡DISTRIBUTED Equivalence

**Module Maven** : root (`.github/workflows`), tous modules (poms), `platform-bootstrap`
**Package** : `com.performance.platform.app.e2e` (test)
**Statut** : WAITING
**Specs de référence** : `docs/knowledge/specs/00-overview.md`, `improvements.md` §1, §4 (T2, T9), §5 (P0)
**Dépend de** : rien
**Issues** : ISSUE-168, ISSUE-169, ISSUE-170

---

## Responsabilité

Fournir le filet de sécurité qui rend la suite de rémédiation irréversible :
un pipeline CI exécutant la suite complète, une convention de paliers de tests
cohérente (D2), et le test d'équivalence LOCAL≡DISTRIBUTED écrit contre le
comportement ACTUEL avant toute fusion (T2).

Ce PDR ne modifie AUCUN code de production. Il crée de la config CI + des tests.

---

## Interfaces Publiques

Aucune interface publique de production. Nouveau type de test uniquement :

```java
// bootstrap/src/test/java/com/performance/platform/app/e2e/LocalDistributedEquivalenceTest.java
@SpringBootTest(classes = PerformancePlatformApplication.class)
class LocalDistributedEquivalenceTest {
    // @ParameterizedTest over { LOCAL (LocalExecutionEngine + InMemoryExecutionTransport),
    //                            DISTRIBUTED (RemoteExecutionEngine + InMemoryExecutionTransport + LocalAgent) }
    // Run the SAME ScenarioDefinition, assert:
    //   - identical final Verdict
    //   - identical ordered task-status sequence (taskId → TaskStatus)
    //   - identical ExecutionStatus
    // MUST PASS against current behaviour (no production change here).
}
```

---

## Règles de Comportement

- **T2 est un test de non-régression écrit CONTRE le comportement actuel** : il
  doit passer aujourd'hui et continuer de passer après la fusion A1 (PDR-050).
- **Tiers de tests (D2 corrigé)** : `*Test` = unitaire (surefire, rapide,
  déterministe, sans conteneur). `*IT` = intégration (failsafe, `-P integration-tests`,
  Testcontainers). Les `*E2ETest` utilisant Testcontainers doivent être reclassés
  explicitement (renommage `*IT` ou exclusion surefire), jamais laissés ambigus.
- **CI (T9)** : `mvn -B verify -P integration-tests` sur chaque push/PR, avec
  Testcontainers disponibles. Le pipeline est le seul garde-fou des ~2 400 tests.

---

## Dépendances Techniques

```
Ce PDR utilise :
  (rien — fondation)

Ce PDR est utilisé par :
  PDR-050 (consolidation)  → dépend de ISSUE-170 (équivalence) comme gate
```

---

## Critères de Done (PDR complet)

- [ ] CI GitHub Actions exécute `mvn verify -P integration-tests` (ISSUE-168)
- [ ] Convention de tiers documentée et appliquée par surefire/failsafe (ISSUE-169)
- [ ] `LocalDistributedEquivalenceTest` passe sur le comportement actuel (ISSUE-170)
