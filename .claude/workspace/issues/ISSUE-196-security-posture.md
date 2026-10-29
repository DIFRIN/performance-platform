# ISSUE-196 — Security Posture Decision + Documentation

**PDR** : PDR-054
**Module** : `platform-bootstrap`
**Statut** : DONE
**Priorité** : P3
**Bloquée par** : —
**Estime** : S (< 1h)

---

## Objectif

Documenter la posture de sécurité (D4) : défaut permissif opt-in, et rendre
l'opt-in explicite (WARN au démarrage si `platform.security.enabled=false`), avec
rôles/authorities sur `/api/**` quand la sécurité est activée.

> ADR requise (ADR-038).

## Fichiers à Modifier

```
bootstrap/src/main/java/com/performance/platform/app/security/
  └── SecurityConfiguration.java     — WARN si désactivé + rôles sur /api/** si activé

bootstrap/src/test/java/com/performance/platform/app/web/
  └── WebSecurityTest.java           — étendre (rôles + WARN)
```

## Règles Spécifiques

- Aucun changement du défaut sans ADR (CNF-03).
- Comportement v1 (permitAll par défaut) conservé mais bruyant (WARN explicite).

## Critères de Done

- [ ] Posture documentée + WARN explicite
- [ ] Rôles sur `/api/**` quand sécurité activée
- [ ] `mvn test -pl :platform-bootstrap -q` → 0 erreur
- [ ] `.claude/workspace/progress.md` mis à jour : ISSUE-196 → DONE
