# PDR-054 — Security Posture Decision

**Module Maven** : `platform-app`
**Package** : `com.performance.platform.app.security`
**Statut** : WAITING
**Specs de référence** : `improvements.md` D4 ; `ADR-019-web-ui-serving-and-security.md` ; `constraints.md` CNF-03
**Dépend de** : rien
**Issues** : ISSUE-196

---

## Responsabilité

Posture de sécurité permissive par défaut : `platform.security.enabled=false` →
`anyRequest().permitAll()`, CSRF désactivé, authentification seule (aucune
autorisation/rôles sur `/api/**`). Acceptable v1, mais c'est un opt-in.

Décision (ADR-038) : documenter la posture, et soit durcir le défaut, soit rendre
l'opt-in explicite et auditable (log WARN au démarrage quand la sécurité est
désactivée ; rôles/authorities sur `/api/**` quand activée).

---

## Interfaces Publiques

Aucune nouvelle interface. Changements de `SecurityConfiguration` :
- WARN au démarrage si `platform.security.enabled=false` (explicite dans les logs).
- Quand activée : ajout d'authorities/rôles sur `/api/**` (ex: `ROLE_ADMIN` pour
  écriture, `ROLE_VIEWER` pour lecture), cohérent avec le JWT existant.

---

## Règles de Comportement

- Aucun changement du défaut sans ADR (CNF-03 : TLS/OAuth2/JWT requis en prod).
- Le comportement v1 (permitAll par défaut) reste disponible mais bruyant (WARN).

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-018 (SecurityConfiguration)

Ce PDR est utilisé par :
  (rien)
```

---

## Critères de Done (PDR complet)

- [ ] Posture documentée + WARN explicite (ISSUE-196)
- [ ] Rôles sur `/api/**` quand la sécurité est activée (ISSUE-196)
- [ ] `mvn test -pl platform-app -q` → 0 erreur
