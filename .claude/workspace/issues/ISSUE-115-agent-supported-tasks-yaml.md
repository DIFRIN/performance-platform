# ISSUE-115 — Add agent.supported-tasks to application-agent.yaml

**PDR** : PDR-026
**Module** : `platform-app`
**Statut** : DONE
**Priorite** : P0 (configuration YAML source de verite pour le mode DISTRIBUTED)
**Bloquee par** : ISSUE-111 (AgentProperties doit exister pour que la propriete soit lue)
**Estime** : S (< 1h)

---

## Objectif

Ajouter la propriete `agent.supported-tasks` dans le profil YAML agent (`application-agent.yaml`) avec une liste vide par defaut. Ajouter egalement la propriete dans `application-orchestrator.yaml` (liste vide documentee). Ajouter des commentaires YAML expliquant le comportement LOCAL vs DISTRIBUTED et le mapping de l'env var `AGENT_SUPPORTED_TASKS`.

## Fichiers a Modifier

```
platform-app/src/main/resources/application-agent.yaml        — ajouter agent.supported-tasks
platform-app/src/main/resources/application-orchestrator.yaml — ajouter agent.supported-tasks (commentee)
```

## Modification dans `application-agent.yaml`

Ajouter la section suivante (par exemple juste apres le bloc `runtime:`) :

```yaml
# ---- Agent specialization (ADR-015: configuration-driven) ----
# La liste supported-tasks definit quelles tasks cet agent execute.
# Vide par defaut: a surcharger par agent via AGENT_SUPPORTED_TASKS env var.
# 
# Mode DISTRIBUTED: cette liste est la source UNIQUE de verite.
#   L'agent n'execute QUE les tasks listees ici.
#   Exemple: AGENT_SUPPORTED_TASKS=mock-server,http-client
# 
# Mode LOCAL: cette propriete est IGNOREE. Le LocalAgent execute
#   automatiquement toutes les tasks du TaskExecutorRegistry.
# 
# Format env var: valeurs separees par des virgules (Spring Boot binding standard)
#   AGENT_SUPPORTED_TASKS=mock-server,http-client,gatling
#   → agent.supported-tasks: [mock-server, http-client, gatling]
agent:
  supported-tasks: []
```

## Modification dans `application-orchestrator.yaml`

Ajouter (documentation seulement -- l'orchestrateur n'a pas de propriete `agent.supported-tasks` active, mais c'est bien de documenter que la propriete existe) :

```yaml
# ---- Agent specialization (documentation) ----
# L'orchestrateur n'execute pas de tasks directement.
# La propriete agent.supported-tasks est IGNOREE en mode ORCHESTRATOR.
# Elle est documentee ici pour reference.
# agent:
#   supported-tasks: []
```

## Verification

- `application-local.yaml` : NE PAS ajouter `agent.supported-tasks`. Le mode LOCAL ignore cette propriete.
- `application.yaml` (commun) : NE PAS ajouter `agent.supported-tasks`. Valeur par defaut fournie par le record `AgentProperties` (liste vide).
- Les commentaires YAML doivent etre clairs sur le mapping env var (ADR-015).

## Critères de Done

- [ ] `application-agent.yaml` contient `agent.supported-tasks: []` avec commentaires
- [ ] `application-orchestrator.yaml` documente la propriete (commentee)
- [ ] Les commentaires expliquent LOCAL vs DISTRIBUTED + mapping `AGENT_SUPPORTED_TASKS`
- [ ] `application-local.yaml` ne contient PAS `agent.supported-tasks`
- [ ] Validation YAML : le fichier est parseable (`yamllint` ou equivalent)
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-115 → DONE
