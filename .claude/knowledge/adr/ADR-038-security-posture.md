# ADR-038 — Posture de securite : permissif opt-in explicite, roles sur /api

**Date** : 2026-10-19
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (D4)
**Raffine** : ADR-018/ADR-019 (serving et securite), CNF-03
**Impacte interfaces publiques** : NON (configuration runtime)

---

## Contexte

Par defaut, `platform.security.enabled=false` produit `anyRequest().permitAll()`,
CSRF desactive, et `/api/**` seulement `authenticated()` quand la securite est
activee. La posture permissive etait silencieuse et l'API n'avait aucune
autorisation par role.

## Décision

- **Defaut inchange** : securite desactivee en LOCAL (CNF-03), mais un WARN
  explicite est emis au demarrage (`action=security_disabled`,
  `reason=platform.security.enabled=false`, hint d'activation).
- **Securite activee** : `/api/**` exige une authentification **et** un role
  `USER` ou `ADMIN` (`hasAnyRole("USER","ADMIN")`).
- Les endpoints publics restent : `/actuator/health/**`, `/`, `/index.html`,
  `/assets/**`.
- CSRF desactive, sessions `STATELESS`, OAuth2/JWT inchange.
- Les fournisseurs OAuth2 doivent mapper leurs claims vers `ROLE_USER` /
  `ROLE_ADMIN` (ou utiliser un mapper dedie). Sans role, l'API repond 403.

## Conséquences

- La posture permissive est visible dans les logs, plus silencieuse.
- Un deploiement DISTRIBUTED existant sans roles dans les tokens doit ajouter
  le mapping avant mise a jour — changement documente.
- `SecurityConfiguration.API_ROLES` est la source unique des roles requis.

## Preuves

- `WebSecurityTest` verifie `API_ROLES`.
- `mvn test -pl platform-app` vert.
