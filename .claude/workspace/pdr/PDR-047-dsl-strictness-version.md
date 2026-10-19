# PDR-047 — DSL Strictness & Version

**Module Maven** : `platform-scenario-dsl`
**Package** : `com.performance.platform.scenario.parser`
**Statut** : WAITING
**Specs de référence** : `improvements.md` B6 ; `.claude/knowledge/specs/01-scenario-dsl.md`
**Dépend de** : PDR-005 (parser existant)
**Issues** : ISSUE-176

---

## Responsabilité

Le DSL accepte silencieusement les typos (`FAIL_ON_UNKNOWN_PROPERTIES=false`),
n'impose pas de version, et ses erreurs n'indiquent pas la ligne fautive. Pour une
plateforme de test, exécuter silencieusement un scénario différent de celui écrit
est le pire mode de défaillance (B6).

Ce PDR rend le parser strict, versionné, et capable de rapporter la ligne
(numéro de ligne SnakeYAML) pour chaque erreur.

---

## Interfaces Publiques

Aucune nouvelle interface publique. Changements internes à `YamlScenarioParser` :

- `ObjectMapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, true)` — toute clé inconnue rejette.
- `scenario.version` devient **requis** et non-blank (le champ existe déjà dans
  `ScenarioYamlDto`/`ScenarioDefinition` ; il passe de « ignoré si absent » à « requis »).
- Les erreurs de `ScenarioParsingException` embarquent le **numéro de ligne**
  (via `org.yaml.snakeyaml.nodes.Node` / `Mark`), format `ligne N : message`.

---

## Règles de Comportement

- Un scénario avec clé inconnue, clé typographiée, mauvais type, ou champ requis
  manquant est **rejeté** avec la ligne fautive (jamais exécuté avec défauts).
- Le `ScenarioValidator` existant reste la source de validation sémantique ; le
  parser est la source de validation **structurelle** stricte.
- La rétro-compatibilité : les scénarios existants valides (sans clé inconnue,
  avec `version`) continuent de parser. Tout scénario sans `version` doit être
  migré (corpus de tests/scénarios d'exemple à mettre à jour).

---

## Dépendances Techniques

```
Ce PDR utilise :
  PDR-005 (ScenarioParser, ScenarioYamlDto, ScenarioParsingException)

Ce PDR est utilisé par :
  PDR-018 (assemblage app — pas de changement)
```

---

## Critères de Done (PDR complet)

- [ ] `FAIL_ON_UNKNOWN_PROPERTIES=true` (ISSUE-176)
- [ ] `scenario.version` requis (ISSUE-176)
- [ ] `ScenarioStrictnessTest` (T7) : corpus rejeté avec ligne fautive (ISSUE-176)
- [ ] Scénarios YAML existants migrés si version absente
- [ ] `mvn test -pl platform-scenario-dsl -q` → 0 erreur
