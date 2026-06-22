# PDR-000 — TEMPLATE (ne pas modifier, copier pour créer un nouveau PDR)

**Module Maven** : `platform-xxx`
**Package** : `com.performance.platform.xxx`
**Statut** : WAITING
**Specs de référence** : `.claude/knowledge/specs/XX.md` sections Y, Z
**Dépend de** : PDR-YYY
**Issues** : ISSUE-XXX, ISSUE-YYY

---

## Responsabilité

[Ce que ce composant fait — 3-5 lignes. Ce qu'il NE fait PAS.]

---

## Interfaces Publiques

```java
// Interfaces, records, enums avec signatures complètes
// Le Developer implémente EXACTEMENT ces signatures
```

---

## Règles de Comportement

- [Règle 1]
- [Règle 2]

---

## Dépendances Techniques

```
Utilise    : PDR-XXX → [quelles classes/interfaces]
Utilisé par: PDR-YYY → [comment]
```

---

## Critères de Done

- [ ] Toutes les Issues du PDR sont DONE
- [ ] Interfaces dans `.claude/context/interfaces-registry.md` → STABLE
- [ ] Tests d'intégration passent (si applicable)
