# ADR-004 — Architecture Hexagonale + DDD

**Date** : 2026-06-05  
**Statut** : ACCEPTED  
**Décideurs** : Équipe Architecture

---

## Contexte

Organisation du code pour maximiser la testabilité et l'évolutivité.

## Décision

Architecture hexagonale (Ports & Adapters) avec DDD par bounded context.
Le domaine est 100% indépendant de tout framework.

## Justification

- `platform-domain` sans Spring permet des tests unitaires en millisecondes.
- Les ports (interfaces) définissent les contrats → les adapters peuvent changer
  sans toucher au domaine (ex: changer PostgreSQL pour MongoDB).
- DDD aligne le code sur le vocabulaire du glossaire → moins de traduction mentale.
- Spring Modulith enforce les frontières de modules → violations détectées à la compilation.

## Conséquences

- Aucune annotation Spring dans `platform-domain` ou `platform-application`.
- Tout I/O passe par un port out (interface dans `platform-application`).
- Les entités de domaine sont des records immuables ou des classes sans framework.
- Mappers nécessaires entre domain records et JPA entities.

## Alternatives Rejetées

- **Architecture en couches classique** : coupling fort entre couches, difficile à tester
  sans démarrer le contexte Spring complet.
- **CQRS + Event Sourcing complet** : sur-engineering pour ce projet, mais CQRS partiel
  (séparation command/query handlers) est retenu.
