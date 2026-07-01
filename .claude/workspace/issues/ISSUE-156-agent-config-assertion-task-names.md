# ISSUE-156 -- Add Assertion Task Names to Agent Configuration

**PDR** : PDR-037
**Module** : `platform-app`, `platform-assertion`
**Statut** : WAITING
**Priorite** : P1 (critique -- sans cette config, les agents distribues ne peuvent pas executer d'assertions)
**Bloquee par** : ISSUE-150 (AssertionExecutor extends TaskExecutor), ISSUE-152 (registre unifie)
**Estime** : M (1-3h)

---

## Objectif

Ajouter les 6 noms d'assertion a la configuration `agent.supported-tasks` dans `application-agent.yaml`. Gerer le conflit de nommage entre `DatabaseTaskExecutor` (PREPARATION, taskName="database") et `DatabaseAssertionExecutor` (ASSERTION, assertionName="database"), qui partagent le meme `getSupportedTaskName()`.

## Fichiers a Modifier

```
platform-app/src/main/resources/
  └── application-agent.yaml     -- ajouter les 6 noms d'assertion a agent.supported-tasks

platform-app/src/main/resources/
  ├── application-local.yaml         -- verifier que les assertions sont supportees en mode LOCAL
  └── application-orchestrator.yaml  -- verifier la config (l'orchestrateur n'execute pas les assertions, les agents oui)

platform-assertion/src/main/java/com/performance/platform/assertion/
  └── database/DatabaseAssertionExecutor.java -- optionnel: renommer getSupportedAssertionName() si conflit

platform-assertion/src/test/java/com/performance/platform/assertion/
  └── AssertionTaskNameUniquenessTest.java -- NOUVEAU: verifier qu'aucun nom d'assertion n'entre en conflit avec un TaskExecutor existant
```

## Configuration

```yaml
# application-agent.yaml -- ajout dans agent.supported-tasks:
agent:
  supported-tasks:
    - performance_test    # existant
    - gatling             # existant
    - database            # existant (PREPARATION: DatabaseTaskExecutor)
    - mock-server         # existant
    - kafka-consumer      # existant
    - kafka-producer      # existant
    - http-client         # existant
    - shell               # existant
    - gatling-metric      # NOUVEAU -- assertion Gatling metrics
    - kafka               # NOUVEAU -- assertion Kafka metrics
    - wiremock            # NOUVEAU -- assertion WireMock request count
    - http-mock           # NOUVEAU -- assertion HTTP mock calls
    - file                 # NOUVEAU -- assertion fichier (EXISTS, CHECKSUM, etc.)
    # NOTE: "database" est DEJA dans la liste (PREPARATION). L'assertion "database"
    # partage le meme nom. Voir resolution de conflit ci-dessous.
```

## Resolution du Conflit "database"

Le `DatabaseTaskExecutor` (PREPARATION, nettoie/peuple la base) et `DatabaseAssertionExecutor` (ASSERTION, verifie des compteurs) ont tous les deux `getSupportedTaskName()` == `"database"`. Avec le registre unifie `DefaultTaskExecutorRegistry`, le dernier enregistre gagne.

**Solution choisie** : Les assertions et les taches de preparation partagent le meme namespace de noms. C'est intentionnel -- un agent qui declare `database` dans `supported-tasks` peut executer LES DEUX types de taches. Le `DefaultTaskExecutorRegistry` gere la collision en ecrasant (dernier enregistre gagne), mais comme les deux implementations sont des beans Spring separes avec des noms de bean differents, Spring les injecte tous les deux dans le registre. Le registre actuel ecrase silencieusement.

**Correction necessaire** : Soit renommer l'un des deux (ex: `DatabaseAssertionExecutor` -> `"db-assert"` ou `DatabaseTaskExecutor` -> garder `"database"`), soit accepter la collision et documenter que seul l'executor d'assertion survivra (car enregistre apres).

**Decision (a confirmer par l'Architect si conteste)** : Renommer `DatabaseAssertionExecutor.getSupportedAssertionName()` de `"database"` a `"database-assertion"`. Cela evite toute ambiguite et aligne avec le pattern ou les noms d'assertion sont distincts des noms de tache. La valeur YAML `task: database-assertion` est plus explicite.

Si cette decision est acceptee, mettre a jour egalement :
- `application-agent.yaml` : `database-assertion` au lieu de `database` pour l'assertion
- `application-local.yaml` : idem
- Tout scenario YAML de test utilisant `task: database` en phase ASSERTION -> `task: database-assertion`
- `DatabaseAssertionExecutor.getSupportedAssertionName()` -> return `"database-assertion"`

## Criteres de Done

- [ ] `mvn test -pl platform-app -q` -> 0 erreur
- [ ] `application-agent.yaml` liste les 6 noms d'assertion (ou 5 + `database-assertion`)
- [ ] `application-local.yaml` reference correctement les noms d'assertion
- [ ] Test : `AssertionTaskNameUniquenessTest` verifie qu'aucun `AssertionExecutor.getSupportedAssertionName()` n'egale un `TaskExecutor.getSupportedTaskName()` (sauf si intentionnel)
- [ ] Le conflit "database" est resolu (renommage en `database-assertion` ou documentation de la collision)
- [ ] Si renommage : tous les fichiers YAML references et `DatabaseAssertionExecutor` sont mis a jour
- [ ] `.claude/workspace/progress.md` mis a jour : ISSUE-156 -> DONE
- [ ] `.claude/workspace/interfaces-registry.md` mis a jour si renommage
