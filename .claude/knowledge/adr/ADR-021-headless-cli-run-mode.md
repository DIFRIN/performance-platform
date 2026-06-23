# ADR-021 — Mode CLI headless (run-and-exit sur `--scenario=`)

**Date** : 2026-06-23
**Statut** : ACCEPTED
**Decideurs** : Architect
**Contexte** : La revue des PDRs Web IHM (PDR-027/028/029) a revele que le mode
"headless run-and-exit sur `--scenario=`" etait suppose existant (PDR-028 le presentait
comme "comportement existant PDR-018") alors qu'il n'existe NULLE PART dans le code :
`PerformancePlatformApplication.main()` execute un `SpringApplication.run` standard,
toujours en mode web. Le critere de Done correspondant dans ISSUE-125 etait donc
insatisfiable et a ete retire. Ce mode est neanmoins desire par l'utilisateur (usage
script/CI). Il est architecturalement significatif car il change la facon dont le fat JAR
bootstrappe (`WebApplicationType`), il faut donc un ADR.

---

## Contexte

Le fat JAR unique (CF-01) doit servir trois modes d'acces a la plateforme, depuis le
meme artefact :

1. **Mode CLI headless** — `java -jar platform-app.jar --scenario=path/to/scenario.yaml`.
   Execute le scenario, imprime un resume sur stdout, sort avec un code de retour
   (0 succes / 1 echec / 2 args invalides). Aucun serveur HTTP, aucune IHM. Usage : CI,
   pipelines, scripts.
2. **Mode API** — `java -jar platform-app.jar` (sans `--scenario=`). Demarre le serveur
   long-running, l'API REST `/api/v1/**` est disponible. Usage : integration programmatique.
3. **Mode IHM** — meme demarrage que le mode API, plus l'IHM Web servie a `/` quand
   `web.ui.enabled=true` (PDR-028). Usage : utilisateurs humains via navigateur.

Aujourd'hui seuls les modes API et IHM sont possibles : `main()` fait toujours un
`SpringApplication.run` standard qui demarre Tomcat (`WebApplicationType.SERVLET` par
defaut des qu'un starter web est present). Il n'existe aucun chemin run-and-exit.

Le mode CLI headless est orthogonal au `RUNTIME_MODE` (LOCAL/DISTRIBUTED) : en LOCAL le
scenario s'execute inline dans la JVM ; en ORCHESTRATOR il s'execute mais dispatche les
taches aux agents. Dans les deux cas, l'application sort une fois l'execution terminee.

---

## Decision

**Le fat JAR detecte la presence de `--scenario=<path>` dans les arguments CLI au
demarrage et bascule en mode headless.**

1. **Detection** : dans `main()`, avant de construire l'application, on inspecte `args`
   a la recherche d'un argument `--scenario=`. Cette detection precede le choix du
   `WebApplicationType`.

2. **Si `--scenario=` est present** → mode headless :
   - L'application demarre avec `WebApplicationType.NONE` via
     `new SpringApplicationBuilder(PerformancePlatformApplication.class).web(WebApplicationType.NONE).run(args)`.
     Aucun Tomcat, aucun port HTTP, aucune IHM.
   - Un bean `ApplicationRunner` (`CliScenarioRunner`) actif uniquement en headless lit
     le chemin du scenario, le parse, lance l'execution via le port in, attend la fin,
     imprime un resume structure sur stdout, puis declenche la sortie de la JVM avec le
     code de retour approprie (`SpringApplication.exit(ctx, () -> exitCode)` suivi de
     `System.exit(exitCode)`).

3. **Si `--scenario=` est absent** → mode serveur (comportement actuel inchange) :
   `WebApplicationType.SERVLET`, serveur long-running, API + IHM (selon `web.ui.enabled`).

4. **Le mode CLI headless n'est applicable qu'en LOCAL et ORCHESTRATOR.** Un nœud AGENT
   (DISTRIBUTED, `MODE=AGENT`) n'est PAS un point d'entrée : il ne reçoit pas de scénario à
   exécuter, il reçoit des `TaskExecutionRequest` via le transport. Le `CliScenarioRunner` ne doit
   donc JAMAIS s'activer en mode AGENT (clarification utilisateur, 2026-06-23 ; voir ADR-019 pour la
   matrice complète mode d'accès × mode runtime). Concrètement, `--scenario=` n'a de sens que là où
   l'application est orchestrateur (LOCAL inline, ou ORCHESTRATOR dispatchant aux agents). En mode
   AGENT, l'application bootstrap déjà avec `WebApplicationType.NONE` (ADR-019) mais sans
   `CliScenarioRunner` : c'est un worker piloté par le transport, pas par un argument CLI.

### Mecanisme

L'adapter entrant `CliScenarioRunner` vit dans `platform-app` (module d'assemblage
racine, deja autorise a heberger les adapters entrants — ADR-019). C'est un adapter
entrant CLI symetrique du `ScenarioController` (adapter entrant HTTP) : il appelle les
**ports in** de `platform-application` (`ScenarioParsingUseCase`, `ExecuteScenarioUseCase`,
`GetExecutionStatusUseCase`). Aucun appel inter-module metier direct, aucun nouveau
module Maven.

La selection du `WebApplicationType` se fait via `SpringApplicationBuilder` dans `main()`
en fonction de la presence de `--scenario=`. On ne tente PAS de la piloter par
`@ConditionalOnProperty` au sein d'un module : le choix du type d'application web doit
etre arrete avant le demarrage du contexte, ce qui impose une decision dans `main()`.

### Codes de sortie

| Code | Signification |
|---|---|
| `0` | Execution terminee, statut `COMPLETED` et toutes les assertions passees |
| `1` | Execution terminee en echec (`FAILED`), ou assertion en echec, ou erreur runtime |
| `2` | Arguments invalides : scenario introuvable, YAML invalide, `--scenario=` vide |

### Sortie stdout

En mode headless, le `CliScenarioRunner` imprime un resume structure et lisible sur
stdout (et non via le logger applicatif, pour rester pilotable en script) :

```
scenario   : <scenario name> (<scenario id>)
execution  : <executionId>
status     : COMPLETED | FAILED
tasks      : <ok> ok / <ko> ko / <total> total
report     : <chemin du rapport genere, si applicable>
exit       : <code>
```

### Impact sur PDR-028 / PDR-029 (IHM)

L'IHM (resource handler, `index.html`, vues) n'est servie que lorsque
`WebApplicationType.SERVLET` est actif. En mode headless (`WebApplicationType.NONE`),
il n'y a aucun contexte servlet, donc aucun resource handler ni controller HTTP n'est
monte : l'IHM est implicitement absente, sans aucune garde supplementaire. La property
`web.ui.enabled` est sans effet en mode headless (pas de contexte servlet a configurer).
PDR-028 doit donc presenter le headless comme un mode a part entiere (PDR-028 plus
ADR-021), et non comme un "comportement existant PDR-018".

---

## Justification

- **Un seul artefact, trois modes** : conforme a CF-01. Le choix du mode est porte par
  un argument CLI explicite, sans variable d'environnement supplementaire ni build separe.
- **Adapter entrant CLI** : `CliScenarioRunner` reutilise les ports in existants, exactement
  comme `ScenarioController`. Pas de logique metier dupliquee, pas de nouveau module.
- **`WebApplicationType.NONE` decide dans `main()`** : c'est le pattern Spring Boot
  standard et le seul endroit ou le type web peut etre fixe avant le bootstrap du contexte.
- **Codes de sortie distincts** : permettent a un pipeline CI de distinguer un echec
  fonctionnel (1) d'une erreur d'invocation (2), sans parser stdout.
- **stdout structure, pas le logger** : la sortie reste deterministe et pilotable meme
  si la configuration de logging change.

---

## Consequences

**Positives** :
- Usage CI/script natif : `java -jar ... --scenario=x.yaml && deploy` fonctionne avec
  les codes de sortie shell standard.
- Aucun port HTTP ouvert en CI (surface reduite, demarrage plus rapide, pas de Tomcat).
- L'IHM et le serveur web ne demarrent jamais par accident en mode batch.
- Symetrie claire des trois modes d'acces, documentee dans README et CLAUDE.md section 9.

**Negatives / Contraintes** :
- `main()` contient desormais une branche de selection du `WebApplicationType` — logique
  de bootstrap legerement plus complexe (a couvrir par test).
- En ORCHESTRATOR headless, l'execution depend de la disponibilite des agents ; un timeout
  d'attente d'agents non satisfait doit se traduire par un code de sortie 1 (echec), pas
  par un blocage indefini. Le `CliScenarioRunner` s'appuie sur les timeouts existants de
  l'engine (taskAvailabilityTimeoutSeconds).
- La sortie via `System.exit` court-circuite le cycle de vie normal ; les hooks de
  shutdown Spring doivent rester fonctionnels (`SpringApplication.exit` ferme le contexte
  proprement avant le `System.exit`).

**Fichiers impactes** :
- `platform-app/.../PerformancePlatformApplication.java` — branche de detection `--scenario=`
  + `SpringApplicationBuilder().web(WebApplicationType.NONE)`.
- `platform-app/.../cli/CliScenarioRunner.java` — nouvel adapter entrant CLI (ApplicationRunner).
- ISSUE-131 (nouvelle) — implementation + test `@SpringBootTest(webEnvironment = NONE)`.
- PDR-028 — section "modes d'acces" alignee : headless = PDR-028 + ADR-021 (pas PDR-018).
- `CLAUDE.md` section 9 — invocation `--scenario=` ajoutee.
- `README.md` — section modes d'acces (CLI / API / IHM).

---

## Alternatives Rejetees

| Alternative | Raison du rejet |
|---|---|
| Piloter `WebApplicationType` via `@ConditionalOnProperty` dans un module | Le type d'application web doit etre fixe avant le bootstrap du contexte ; un conditional intra-module est trop tardif. |
| Deux artefacts separes (CLI vs serveur) | Viole CF-01 (un seul JAR). Double la maintenance et le packaging. |
| Variable d'environnement `HEADLESS=true` plutot qu'un argument CLI | L'argument `--scenario=` porte deja l'intention ET le chemin ; une seconde variable serait redondante et source de divergence (ADR-006). |
| Toujours demarrer le serveur puis appeler l'API en interne | Demarre Tomcat inutilement en CI ; surface et latence superflues ; ne donne pas de code de sortie. |
