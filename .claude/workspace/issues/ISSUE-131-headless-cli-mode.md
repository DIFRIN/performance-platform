# ISSUE-131: Mode CLI headless (run-and-exit sur `--scenario=`)

**PDR** : PDR-028
**Module** : `platform-app`
**Statut** : WAITING
**Priorité** : P1
**Bloquée par** : ISSUE-125
**Taille** : M
**Estime** : M

---

## Objectif

Ajouter le mode CLI headless : `java -jar platform-app.jar --scenario=path/to/scenario.yaml`
parse et execute le scenario, imprime un resume structure sur stdout, et sort avec un code
de retour (0 succes / 1 echec / 2 args invalides), SANS demarrer Tomcat ni l'IHM. En
l'absence de `--scenario=`, le comportement serveur actuel (API + IHM) reste strictement
inchange. Voir ADR-021.

---

## Fichiers à Créer / Modifier

```
platform-app/src/main/java/com/performance/platform/
  └── PerformancePlatformApplication.java   — MODIF : main() detecte --scenario= ;
                                               si present → SpringApplicationBuilder
                                               .web(WebApplicationType.NONE).run(args) ;
                                               sinon → SpringApplication.run (inchange)

platform-app/src/main/java/com/performance/platform/app/cli/
  └── CliScenarioRunner.java                — ApplicationRunner, actif uniquement en headless :
                                               lit --scenario, parse, execute, poll, print, exit

platform-app/src/test/java/com/performance/platform/app/cli/
  └── CliScenarioRunnerHeadlessTest.java    — @SpringBootTest(webEnvironment = NONE) :
                                               charge un scenario test, assert exit code + stdout
```

---

## Interfaces à Implémenter

> Ports in existants (platform-application), a appeler — ne PAS les modifier :

```java
public interface ScenarioParsingUseCase {
    ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException;
}

public interface ExecuteScenarioUseCase {
    ExecutionId execute(ScenarioDefinition scenario) throws ExecutionException;
}

public interface GetExecutionStatusUseCase {
    ExecutionStatus getStatus(ExecutionId id);
    Optional<ExecutionState> getState(ExecutionId id);
}
```

Bootstrap dans `main()` (mode headless) :

```java
public static void main(String[] args) {
    if (hasScenarioArg(args)) {
        new SpringApplicationBuilder(PerformancePlatformApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        // CliScenarioRunner (ApplicationRunner) gere l'execution puis SpringApplication.exit + System.exit
    } else {
        SpringApplication.run(PerformancePlatformApplication.class, args); // serveur, inchange
    }
}
```

`CliScenarioRunner` (ApplicationRunner, conditionnel a la presence de `--scenario=`) :

```java
@Component
@ConditionalOnProperty(name = "scenario")   // bind relaxe de --scenario= ; n'est jamais cree en mode serveur
public class CliScenarioRunner implements ApplicationRunner {
    @Override public void run(ApplicationArguments args) { /* parse → execute → poll → print → exit */ }
}
```

---

## Règles Spécifiques

- **Detection `--scenario=`** : faite dans `main()` AVANT le choix du `WebApplicationType`
  (le type web doit etre fixe avant le bootstrap du contexte — ADR-021). En mode headless :
  `SpringApplicationBuilder(...).web(WebApplicationType.NONE).run(args)`. AUCUN Tomcat ne
  doit demarrer.
- **`CliScenarioRunner`** :
  1. lit le chemin via `args.getOptionValues("scenario")` (ou `@Value("${scenario}")`) ;
  2. charge le contenu YAML depuis le chemin (filesystem ; reutiliser `ResourceLoader`
     conformement a ADR-013, pas de `Files.readString` brut) ;
  3. `ScenarioParsingUseCase.parse(yaml)` → `ExecuteScenarioUseCase.execute(scenario)` ;
  4. polle `GetExecutionStatusUseCase.getStatus(id)` jusqu'a un statut terminal
     (`COMPLETED` / `FAILED`) en s'appuyant sur les timeouts existants de l'engine
     (pas de boucle infinie : un statut non terminal au-dela du timeout → echec, code 1) ;
  5. imprime le resume structure sur **stdout** (System.out, PAS le logger) ;
  6. sort via `SpringApplication.exit(applicationContext, () -> exitCode)` puis
     `System.exit(exitCode)` (fermeture propre du contexte avant exit).
- **Codes de sortie** (ADR-021) :
  - `0` = statut terminal `COMPLETED` ET toutes les assertions passees ;
  - `1` = `FAILED`, assertion en echec, timeout d'execution, ou exception runtime ;
  - `2` = `--scenario=` vide, fichier introuvable, ou YAML invalide (parsing exception).
- **`web.ui.enabled` est sans effet en mode headless** : pas de contexte servlet, donc
  aucun resource handler ni controller. Ne PAS reintroduire de garde IHM cote headless.
- **Resume stdout** — format exact :
  ```
  scenario   : <name> (<id>)
  execution  : <executionId>
  status     : COMPLETED | FAILED
  tasks      : <ok> ok / <ko> ko / <total> total
  report     : <chemin rapport si genere, sinon "-">
  exit       : <code>
  ```
- **Applicable a LOCAL et ORCHESTRATOR uniquement, JAMAIS en AGENT (ADR-021/ADR-019)** :
  en LOCAL le scenario s'execute inline ; en ORCHESTRATOR il dispatche aux agents. Dans ces
  deux cas le `CliScenarioRunner` appelle les ports in et attend le statut terminal. En revanche
  un nœud AGENT (DISTRIBUTED, `MODE=AGENT`) n'est PAS un point d'entree : il recoit des
  `TaskExecutionRequest` via le transport, pas un `--scenario=`. Le `CliScenarioRunner` ne doit
  donc JAMAIS s'activer en mode AGENT. Concretement : en plus de `@ConditionalOnProperty(name = "scenario")`,
  garder le runner inactif quand `RUNTIME_MODE=AGENT` (ex. condition combinee sur `runtime.mode`,
  ou refus explicite dans `main()` si `--scenario=` est present alors que le mode est AGENT →
  code de sortie 2, args invalides). Le mode AGENT bootstrap deja en `WebApplicationType.NONE`
  (ADR-019), mais sans `CliScenarioRunner`.
- **Mode serveur inchange** : sans `--scenario=`, `main()` doit faire exactement le
  `SpringApplication.run` actuel ; `CliScenarioRunner` ne doit pas etre instancie.

---

## Critères de Done

- [ ] `mvn test -pl platform-app -q` → 0 erreur, 0 warning
- [ ] `java -jar platform-app/target/*.jar --scenario=<scenario valide>` → execute, imprime
      le resume, sort avec code 0 (succes) ; aucun port HTTP ouvert (pas de Tomcat)
- [ ] Scenario en echec → code de sortie 1 ; scenario introuvable / YAML invalide → code 2
- [ ] Sans `--scenario=` → comportement serveur inchange (API + IHM selon `web.ui.enabled`),
      `CliScenarioRunner` non instancie
- [ ] Mode AGENT (`MODE=AGENT`) : `CliScenarioRunner` JAMAIS instancie, meme avec `--scenario=`
      (ADR-021) ; l'agent reste un worker pilote par le transport
- [ ] Test `@SpringBootTest(webEnvironment = NONE)` : charge un scenario test, assert le
      statut terminal, le code de sortie attendu et le contenu stdout
- [ ] `.claude/workspace/progress.md` : ISSUE-131 → IN REVIEW (via `bash .claude/scripts/issue-finish.sh`)
- [ ] `.claude/workspace/interfaces-registry.md` mis à jour (CliScenarioRunner, branche bootstrap headless)
- [ ] `.claude/workspace/current-issue.md` : statut reflète l'état réel
