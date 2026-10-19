# ADR-032 — Détection du rôle AGENT au bootstrap, y compris les arguments CLI

**Date** : 2026-10-19
**Statut** : ACCEPTED
**Décideurs** : Architect + implémentation (Claude Code), sur constat de l'audit `improvements.md` (B5)
**Raffine** : ADR-006 (priorité env > property), ADR-019 (AGENT sans serveur web), ADR-021 (CLI headless)
**Impacte interfaces publiques** : NON (ajout d'une fonction package-private testable)

---

## Contexte

`PerformancePlatformApplication.isAgentMode()` ne consultait que la variable
d'environnement `MODE` et la propriété système `runtime.role`. Un lancement
`java -jar app.jar --runtime.role=AGENT` démarrait donc **avec** un serveur web,
contrairement à la garantie ADR-019 (« la garantie est au niveau JVM »).

Le choix du `WebApplicationType` précède la construction de l'`Environment`
Spring : les arguments CLI doivent être inspectés directement dans `main()`.

## Décision

`detectAgentRole(args, envMode, roleProperty)` résout, dans l'ordre :

1. variable d'environnement `MODE` ;
2. propriété système `runtime.role` (`-Druntime.role=AGENT`) ;
3. argument CLI `--runtime.role=AGENT` ;
4. argument CLI `--spring.profiles.active=agent` (le profil `agent` définit
   `runtime.role: AGENT`).

La fonction est pure et testée (`PerformancePlatformApplicationTest`).

## Conséquences

- `--runtime.role=AGENT` et `--spring.profiles.active=agent` forcent
  `WebApplicationType.NONE`.
- `RuntimeConfigResolver` est la source unique : constantes, priorité et
  parsing sont partagés avec `RuntimeModeResolver` (facade)
  et `RuntimeConfigEnvironmentPostProcessor`.
- Priorité unifiée : env > argument CLI > property > défaut.

## Preuves

- `PerformancePlatformApplicationTest` : env, sysprop, CLI role, profil agent.
- `mvn test -pl platform-app` vert (dont `OrchestratorContextStartupTest`).
