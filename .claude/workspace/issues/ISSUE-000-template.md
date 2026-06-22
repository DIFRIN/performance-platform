# ISSUE-000 — TEMPLATE (ne pas modifier, copier pour créer une nouvelle Issue)

**PDR** : PDR-XXX
**Module** : `platform-xxx`
**Statut** : WAITING
**Priorité** : P0 | P1 | P2
**Bloquée par** : ISSUE-YYY
**Taille** : S | M | L

---

## Objectif

[Ce que cette Issue produit. Ce que le Developer peut vérifier à la fin. 1-2 phrases.]

---

## Fichiers à Créer / Modifier

```
platform-xxx/src/main/java/com/performance/platform/xxx/
  ├── NomClasse.java       — [rôle en 1 ligne]
  └── AutreClasse.java     — [rôle en 1 ligne]

platform-xxx/src/test/java/com/performance/platform/xxx/
  └── NomClasseTest.java   — [ce qui est testé]
```

---

## Interfaces à Implémenter

```java
// Copié depuis le PDR parent — signatures exactes
```

---

## Règles Spécifiques

- [Règle si nécessaire]

---

## Critères de Done

- [ ] `mvn test -pl platform-xxx -q` → 0 erreur, 0 warning
- [ ] [Critère métier 1]
- [ ] [Critère métier 2]
- [ ] `.claude/workspace/progress.md` : ISSUE-XXX → IN REVIEW
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour
- [ ] `.claude/workspace/session-state.md` mis à jour
