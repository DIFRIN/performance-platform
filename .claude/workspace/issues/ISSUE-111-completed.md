# ISSUE-111: Create AgentProperties @ConfigurationProperties record
**Status**: APPROVED
**PDR**: PDR-026
**Module**: platform-app
**Started**: 2026-06-22T12:00:00+02:00

## Objectif

Creer le record `AgentProperties` avec `@ConfigurationProperties(prefix = "agent")` qui lit la propriete `agent.supported-tasks` depuis le YAML et/ou la variable d'environnement `AGENT_SUPPORTED_TASKS`. Ce record est la source unique de verite pour la specialisation des agents en mode DISTRIBUTED.

## Fichiers a Creer

```
platform-app/src/main/java/com/performance/platform/app/config/
  └── AgentProperties.java           — @ConfigurationProperties record

platform-app/src/test/java/com/performance/platform/app/config/
  └── AgentPropertiesTest.java       — tests de binding YAML + env var
```

## Reviewer Feedback
(None yet)
