# ADR-015 — Configuration-Driven Agent Specialization (replaces ADR-008)

**Date** : 2026-06-22
**Statut** : ACCEPTED
**Décideurs** : Architect
**Contexte** : Introduction de PDR-025 (Mock Agent Demo Scenarios) a revele une ambiguite dans ADR-008 : la source des `supportedTaskNames` n'etait pas formellement contrainte. Risque d'auto-discovery depuis les annotations.

---

## Contexte

ADR-008 a etabli le principe de filtrage cote agent (broadcast + `TaskSpecializationFilter`). Ce principe est valide et conserve. Mais ADR-008 n'a pas tranche explicitement COMMENT les `supportedTaskNames` sont peuples. La spec laissait la porte ouverte a une auto-discovery basee sur le scan des annotations `@Preparation`/`@Injection`/`@Assertion` des `TaskExecutor` charges.

Deux sources potentielles etaient en concurrence :
1. **Annotations** : `AnnotationScanner` trouve les executors, extrait leur `name()` -> `supportedTaskNames`
2. **Configuration YAML** : `agent.supported-tasks` dans `application.yml`

Cette ambiguite est devenue critique avec PDR-025, qui exige que des plugins charges (avec leurs annotations) puissent etre volontairement desactives (idle) si la config de l'agent ne les liste pas. Auto-discovery depuis les annotations rendrait impossible ce comportement.

## Decision

**Les `supportedTaskNames` de `AgentDescriptor` proviennent EXCLUSIVEMENT de la configuration `agent.supported-tasks` dans `application.yml`. Les annotations `@Preparation`/`@Injection`/`@Assertion` sont conservees UNIQUEMENT pour le `PluginLoader` (resolution task-name -> implementation). Elles ne sont JAMAIS utilisees pour deriver `supportedTaskNames`.**

ADR-008 est SUPERSEDED par cet ADR. Le principe de filtrage cote agent (broadcast, pas de `AgentAllocator`) est CONSERVE et REPRIS dans cet ADR.

### Detail du modele

```
Configuration (application.yml)     Runtime (AgentDescriptor)
────────────────────────────────    ──────────────────────────
agent:                              AgentDescriptor {
  supported-tasks:                      supportedTaskNames: ["mock-server", "http-client"]
    - mock-server                   }
    - http-client

ETAPE 1: PluginLoader scanne les annotations pour trouver l'implementation du task-name
ETAPE 2: AgentDescriptor.supportedTaskNames vient de la config — PAS des annotations
ETAPE 3: TaskSpecializationFilter filtre les TaskExecutionRequest selon supportedTaskNames
```

### Comportement par mode

**LOCAL mode** :
- Une seule instance JVM = orchestrateur + agent + tous les TaskExecutors
- `LocalAgent` declare TOUS les `supportedTaskNames` derives du `TaskExecutorRegistry` (tous les noms enregistres)
- `LocalAgent.canExecute()` retourne toujours `true`
- Specialisation non-pertinente en LOCAL : tout est disponible par definition
- Aucun champ `agentTags` dans les scenarios LOCAL
- Tout step est execute in-process, sans dispatch

**DISTRIBUTED mode** :
- Chaque agent declare explicitement `agent.supported-tasks` dans sa config (`application-agent.yaml` ou variable d'environnement `AGENT_SUPPORTED_TASKS`)
- L'orchestrateur dispatche chaque `TaskExecutionRequest` en broadcast (pas de selection ciblee)
- Chaque agent filtre localement via `TaskSpecializationFilter` base sur `supportedTaskNames`
- Le routing est par `taskName` uniquement — JAMAIS par tags
- `agentTags` est COMPLETEMENT SUPPRIME des scenarios — le champ n'existe plus
- Plusieurs agents peuvent avoir la meme task dans `supported-tasks` (multi-claim, gere par `TaskCompletionPolicy`, ADR-011)

### Mapping env var -> config

La variable d'environnement `AGENT_SUPPORTED_TASKS` (liste de task names separee par des virgules) mappe a la propriete Spring `agent.supported-tasks` (YAML list). Mapping via `@ConfigurationProperties(prefix = "agent")` avec un champ `List<String> supportedTasks`.

### Plugins idle si non configures

Si un plugin JAR contient un `TaskExecutor` avec `@Preparation(name = "mon-plugin")` mais que `agent.supported-tasks` ne contient PAS `mon-plugin` :
- Le `PluginLoader` charge l'executor
- Le `TaskExecutorRegistry` l'enregistre normalement
- L'agent ne l'execute JAMAIS car `TaskSpecializationFilter` le rejette
- L'executor est "idle" — charge, pret, jamais active

### Annotations : usage conserve

| Usage | Conserve ? | Detail |
|---|---|---|
| PluginLoader: resolution task-name -> implementation | OUI | `AnnotationScanner` trouve l'executor par son `name` |
| Derivation automatique de `supportedTaskNames` | NON | Les `supportedTaskNames` viennent de `agent.supported-tasks` |
| Enregistrement dans `TaskExecutorRegistry` | OUI | Le `name` de l'annotation = cle dans le registre |
| Validation CF-07 (annotation obligatoire) | OUI | Tout TaskExecutor doit avoir exactement une annotation |

## Justification

1. **Separation des sources de verite** : Ce qui est sur le classpath (plugins, executors) est independant de ce que l'agent choisit d'executer. Deux agents avec le meme artefact peuvent avoir des roles differents par simple configuration.
2. **Un seul artefact (CF-01)** : Le principe "meme JAR, config differente = role different" est preserve. Sans config-driven, chaque agent devrait avoir un JAR different (ou les plugins differents).
3. **Plugin idle sans effet de bord** : Un plugin charge mais non reference ne consomme pas de ressources d'execution — il n'est jamais active. Utile pour les images Docker standardisees.
4. **Rollback/rollout progressif** : Changer la liste `agent.supported-tasks` et redeployer suffit pour modifier les capacites. Pas besoin de reconstruire le JAR.
5. **LOCAL mode simplifie** : Pas de configuration de specialisation necessaire en local. Tout est automatiquement disponible.
6. **Annotations preservees pour leur usage legitime** : La resolution task-name -> implementation par `PluginLoader` reste un besoin distinct de la specialisation. Les annotations sont la bonne solution pour ce besoin-la.

## Consequences

**Positives** :
- Ambiguite levee : une seule source de verite pour `supportedTaskNames` (la config)
- `agentTags` elimine des scenarios — un seul mecanisme de routing (`task:` name)
- Mode LOCAL trivial : zero config de specialisation
- Images Docker standardisees avec plugins "idle" possibles
- Le principe CF-01 (meme artefact) est renforce

**Negatives / Contraintes** :
- Si `agent.supported-tasks` n'est pas correctement configure, des tasks valides ne seront jamais executees (fail-silent — mais loggue NOT_RESPONSIBLE)
- Le `PluginLoader` et `TaskExecutorRegistry` doivent etre conscients que leur contenu n'est pas la source de `supportedTaskNames` (documentation necessaire)
- ADR-008 est SUPERSEDED — tout document ou code referencant ADR-008 doit etre mis a jour
- PDR-009 et PDR-018 doivent etre verifies pour implementer ce modele (deja fait dans le code actuel — `supportedTaskNames` est deja un parametre externe, pas auto-decouvert)

**Fichiers impactes** :
- `.claude/knowledge/adr/ADR-008-specialized-agent-filter.md` — marquer comme SUPERSEDED BY ADR-015
- `.claude/knowledge/adr/ADR-015-configuration-driven-agent-specialization.md` — ce fichier (nouveau)
- `.claude/knowledge/specs/04-agent-runtime.md` — mettre a jour la section sur `supportedTaskNames` (source explicite = config)
- `.claude/knowledge/glossary.md` — verifier les entrees `agentTags`, `supportedTaskNames`
- `.claude/workspace/interfaces-registry.md` — section "Model: Agent Specialization" deja a jour
- `.claude/workspace/progress.md` — ajouter note follow-up PDR-009/PDR-018
- `CLAUDE.md` — ajouter ADR-015 a la table de routing

**Modules impactes (aucun changement de code immediat — le code actuel est deja compatible)** :

| Module | Impact | Detail |
|---|---|---|
| `platform-domain` | Aucun | `AgentDescriptor.supportedTaskNames` est un champ neutre (accepte n'importe quelle source) |
| `platform-plugin-api` | Documentation | Les annotations restent inchangees ; specifier dans le javadoc qu'elles ne derivent pas `supportedTaskNames` |
| `platform-agent-runtime` | Verification | `DefaultTaskSpecializationFilter`, `LocalAgent`, `DistributedAgentRuntime` utilisent deja `supportedTaskNames` en parametre — le code est deja configuration-driven |
| `platform-app` | Implementation Spring | Creer `@ConfigurationProperties(prefix = "agent")` avec `List<String> supportedTasks` ; ajouter `agent.supported-tasks` dans `application-agent.yaml` ; mapper `AGENT_SUPPORTED_TASKS` env var |
| `platform-deployment` | Docker Compose | `AGENT_SUPPORTED_TASKS` au lieu de `AGENT_TAGS` (deja dans PDR-025) |
| `platform-scenario-dsl` | Nettoyage | Supprimer tout support de `agentTags` dans le parser/scenario YAML |

### Migration Path

1. **PDR-025/ISSUE-105-108** : Les scenarios LOCAL/DISTRIBUTED deja ecrits sans `agentTags` — OK
2. **PDR-009/PDR-018 follow-up** : Verifier que le mapping Spring `AGENT_SUPPORTED_TASKS` -> `agent.supported-tasks` est implemente. Le code Java actuel (`DefaultTaskSpecializationFilter`, `LocalAgent`, `DistributedAgentRuntime`) est deja compatible — il recoit `supportedTaskNames` en parametre de constructeur. Le travail restant est le cablage Spring.
3. **PDR-019 (Docker Compose)** : Remplacer toute reference a `AGENT_TAGS` par `AGENT_SUPPORTED_TASKS` dans les docker-compose existants (si applicable).
4. **Aucun changement de code dans `platform-domain`, `platform-agent-runtime`** : les interfaces et records sont deja correctement structures.

## Alternatives Rejetees

| Alternative | Raison du rejet |
|---|---|
| Auto-discovery des `supportedTaskNames` depuis les annotations | Empeche les plugins idle, viole le principe "meme artefact, roles differents par config", rend impossible la desactivation d'un executor charge |
| Champ `agentTags` dans les scenarios YAML | Deux mecanismes de routing concurrents (tags + task name) = complexite et confusion. Le `task:` name est suffisant et non-ambigu |
| Derivation hybride (annotations + config merge) | Source de verite duale = comportement non deterministe. Quel source gagne en cas de conflit ? |
| Supprimer les annotations (`@Preparation`/etc) | Les annotations sont necessaires pour PluginLoader (task-name -> implementation). Les supprimer casserait la resolution des plugins |

## Relations avec les autres ADRs

| ADR | Relation |
|---|---|
| ADR-007 (Plugin JAR) | Annotations conservees pour PluginLoader — aucun changement |
| ADR-008 (Specialized Agent Filter) | **SUPERSEDED** — le principe de filtrage est conserve mais ce ADR-015 precise la source exclusive de `supportedTaskNames` |
| ADR-009 (Kafka Consumer Group) | Inchange — le consumer group par agent reste valide |
| ADR-011 (Multi-claim) | Inchange — plusieurs agents avec la meme task dans `supported-tasks` = multi-claim naturel |
| ADR-012 (Agent Lifecycle Event) | Inchange — `supportedTaskNames` est toujours dans le payload d'enregistrement |
| ADR-013 (Spring-first) | Renforce — `@ConfigurationProperties` pour `agent.supported-tasks` |
| ADR-014 (Datasource Config) | Non concerne |
