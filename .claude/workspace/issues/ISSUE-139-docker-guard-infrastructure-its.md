# ISSUE-139 — Garde Docker sur les ITs de platform-infrastructure

**PDR** : PDR-032
**Module** : `platform-infrastructure`
**Statut** : WAITING
**Priorité** : P1 (critique)
**Bloquée par** : —
**Estime** : S (< 1h)

---

## Objectif

Ajouter `@Testcontainers(disabledWithoutDocker = true)` aux 4 ITs Testcontainers de
`platform-infrastructure` pour qu'ils soient **skipped** (et non en erreur) en l'absence de
Docker (ADR-023).

## Fichiers à Créer / Modifier

```
MODIFIER (tests) :
platform-infrastructure/src/test/java/.../DatabaseTaskExecutorIT.java
platform-infrastructure/src/test/java/.../KafkaTaskExecutorsIT.java
platform-infrastructure/src/test/java/.../EntitiesMappingIT.java
platform-infrastructure/src/test/java/.../JpaExecutionRepositoryIT.java
  — remplacer @Testcontainers par @Testcontainers(disabledWithoutDocker = true)
```

## Interfaces à Implémenter

```java
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseTaskExecutorIT { /* @Container ... inchangé */ }
```

## Règles Spécifiques

- Modifier **uniquement** l'annotation `@Testcontainers` (ajout du paramètre). Ne pas changer
  la logique de test, les `@Container`, ni les assertions.
- Ne pas utiliser `Assumptions.assumeTrue(...)` (ADR-023 : garde au niveau classe).
- Vérifier que chaque IT a bien `import org.testcontainers.junit.jupiter.Testcontainers;`.

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` (sans Docker) → vert, les 4 IT `skipped`
- [ ] `mvn verify -pl platform-infrastructure -P integration-tests` (avec Docker) → les 4 IT s'exécutent
- [ ] Les 4 classes portent `@Testcontainers(disabledWithoutDocker = true)`
- [ ] `.claude/workspace/progress.md` : géré par les scripts (`issue-finish.sh`)
</content>
