# ISSUE-119: Etendre ExecutionRepository (findAll/deleteById) + JpaExecutionRepository

**PDR** : PDR-027
**Module** : `platform-infrastructure`
**Statut** : APPROVED
**Priorité** : P0
**Bloquée par** : —
**Taille** : M
**Estime** : M

---

## Objectif

Etendre le port sortant `ExecutionRepository` avec `findAll(int limit)` et `deleteById(ExecutionId)`,
puis implementer ces methodes dans `JpaExecutionRepository` (tri par startedAt desc, suppression transactionnelle
de l'execution + ses resultats de task). Le Developer peut verifier via tests d'integration que le listing
est trie/borne et que la suppression efface bien execution + resultats.

---

## Fichiers à Créer / Modifier

```
platform-application/src/main/java/com/performance/platform/application/ports/out/
  └── ExecutionRepository.java         — AJOUT findAll(int) + deleteById(ExecutionId)

platform-infrastructure/src/main/java/com/performance/platform/infrastructure/persistence/
  └── JpaExecutionRepository.java      — implementation des 2 nouvelles methodes (+ requete/repo Spring Data si besoin)

platform-infrastructure/src/test/java/com/performance/platform/infrastructure/persistence/
  └── JpaExecutionRepositoryTest.java  — tests findAll (tri/limit) + deleteById (cascade resultats)
```

---

## Interfaces à Implémenter

```java
// platform-application — ajout au port existant (ne pas casser les methodes existantes)
public interface ExecutionRepository {
    // ... existant : save, findById, updatePhase, saveTaskResult, getTaskResults ...

    List<ExecutionState> findAll(int limit);   // tri startedAt desc, borne dans la requete

    void deleteById(ExecutionId id);           // transactionnel, no-op si absent
}
```

---

## Règles Spécifiques

- `findAll(limit)` : tri par `startedAt` DESC, `limit` applique dans la requete JPA (pas en memoire).
- `deleteById` : `@Transactional`, supprime l'execution ET ses resultats de task (cascade ou suppression explicite). No-op silencieux si absent.
- Respecter ADR-014 (datasource) et le confinement JPA (ArchUnit existant — pas de fuite `@Entity` hors persistence).
- Tests d'integration Testcontainers PostgreSQL.

---

## Critères de Done

- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur, 0 warning
- [ ] `findAll(limit)` retourne les N executions les plus recentes triees desc
- [ ] `deleteById` supprime execution + resultats ; no-op si id inconnu
- [ ] Methodes existantes du port inchangees (compilation des modules consommateurs OK)
- [ ] `.claude/workspace/progress.md` : ISSUE-119 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (ExecutionRepository etendu)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel
