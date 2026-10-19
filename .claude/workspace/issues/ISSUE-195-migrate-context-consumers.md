# ISSUE-195 — Migrate Consumers off Type-Erased Context Getters

**PDR** : PDR-053
**Module** : `platform-execution-engine`, `platform-reporting`, `platform-assertion`
**Statut** : WAITING
**Priorité** : P2
**Bloquée par** : ISSUE-194
**Estime** : M (1-3h)

---

## Objectif

Migrer les consommateurs d'`ExecutionContext` des getters type-erasés
(`get(taskId, agentId, Class<T>)` / `getFirst(taskId, Class<T>)`) vers les clés
nommées (`getOutput` / `getFirstOutput`), puis supprimer les méthodes dépréciées.

## Fichiers à Modifier

```
platform-execution-engine/.../PartialContextBuilder.java   — clés nommées
platform-execution-engine/.../LocalExecutionEngine.java    — (ou ExecutionEngine post-fusion)
platform-reporting/.../*.java                              — clés nommées
platform-assertion/.../*.java                              — clés nommées
platform-domain/.../ExecutionContext.java                  — supprimer get(Class)/getFirst(Class)
```

## Règles Spécifiques

- Aucun changement de comportement métier (les clés de sortie documentées par executor).
- Chaque executor documente les noms de ses outputs (contrat).

## Critères de Done

- [ ] Aucun appel restant à `get(Class)`/`getFirst(Class)`
- [ ] `mvn test -pl platform-domain,platform-execution-engine,platform-reporting,platform-assertion -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-195 → DONE
