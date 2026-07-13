# PDR-041 — Task Executor Audit & Doctrine

**Module Maven** : `platform-infrastructure`
**Package** : `com.performance.platform.infrastructure.executor`
**Status** : WAITING
**Specs de reference** : `.claude/knowledge/specs/03-task-framework.md`
**Depend de** : PDR-010 (TaskExecutorRegistry — déjà DONE)
**Issues** : ISSUE-165

---

## Responsabilite

Produire un ADR documentant la doctrine d'utilisation des TaskExecutors : quand utiliser un executor dédié (avec garde-fous, validation, cycle de vie) vs `ShellTaskExecutor` (flexibilité, one-off). Ce PDR ne supprime aucun code — il documente et audite l'existant.

L'audit vérifie aussi qu'il n'y a pas de duplication fonctionnelle entre executors.

---

## État des Lieux — Executors Existants

| Executor | Module | Annotation | taskName | Garde-fous |
|---|---|---|---|---|
| `DatabaseTaskExecutor` | infra | `@Preparation` | `database` | Validation SQL (regex table name), timeout, ResourceDatabasePopulator |
| `DockerTaskExecutor` | infra | `@Preparation` | `docker` | Lifecycle (start/stop/healthcheck), cleanup auto au restart |
| `FilesystemTaskExecutor` | infra | `@Preparation` | `filesystem` | Sandboxing (path validation), opérations typées (CREATE/DELETE/UPLOAD/CLEANUP) |
| `HttpClientTaskExecutor` | infra | `@Preparation` | `http-client` | Timeout, retry HTTP, validation URL |
| `KafkaConsumerTaskExecutor` | infra | `@Preparation` | `kafka-consumer` | Consumer group, offset management, cluster registry |
| `KafkaProducerTaskExecutor` | infra | `@Preparation` | `kafka-producer` | Cluster registry, KafkaTemplate, delivery callback |
| `MockServerTaskExecutor` | infra | `@Preparation` | `mock-server` | WireMock lifecycle (start/stop/reset), stubs via JSON |
| `ShellTaskExecutor` | infra | `@Preparation` | `shell` | Timeout, working directory, exit code check |
| `GatlingTaskExecutor` | injection-gatling | `@Injection` | `performance_test` | Gatling runner, result parser, simulation lifecycle |

---

## Doctrine (à formaliser dans l'ADR)

### Quand utiliser un Executor DÉDIÉ

1. **Opérations structurées avec validation** : L'opération a des paramètres bien définis qui doivent être validés (ex: `DatabaseTaskExecutor` valide le nom de table contre injection SQL)
2. **Cycle de vie stateful** : L'opération nécessite start/stop/cleanup (ex: `MockServerTaskExecutor`, `DockerTaskExecutor`)
3. **Intégration avec une API spécifique** : L'opération utilise une bibliothèque qui nécessite une configuration particulière (ex: `KafkaConsumerTaskExecutor` avec consumer groups)
4. **Opérations fréquentes et critiques** : L'opération est utilisée dans la majorité des scénarios et mérite une DX soignée

### Quand utiliser ShellTaskExecutor

1. **Opérations one-off/ad-hoc** : L'opération est spécifique à un scénario, pas réutilisable
2. **Prototypage rapide** : Pendant le développement, avant de créer un executor dédié
3. **Commandes système simples** : `curl`, `wget`, scripts custom non critiques
4. **Absence d'API Java** : L'outil n'a pas de SDK Java utilisable

### Règle de décision

```
┌─────────────────────────────────────────────────────┐
│ L'opération a-t-elle des paramètres à valider ?      │
│  OUI → Executor dédié                                │
│  NON → Question suivante                             │
├─────────────────────────────────────────────────────┤
│ L'opération nécessite-t-elle start/stop/cleanup ?    │
│  OUI → Executor dédié                                │
│  NON → Question suivante                             │
├─────────────────────────────────────────────────────┤
│ L'opération est-elle réutilisable dans ≥ 2 scénarios │
│ ET critique (sécurité, données) ?                     │
│  OUI → Executor dédié                                │
│  NON → ShellTaskExecutor                             │
└─────────────────────────────────────────────────────┘
```

---

## Résultat de l'Audit — Pas de Duplication

| Fonctionnalité | Executor dédié | Peut être fait via Shell ? | Verdict |
|---|---|---|---|
| Purge/populate DB | `DatabaseTaskExecutor` | Oui (`psql -f script.sql`) | **Garder** : validation SQL, injection protection, intégration JdbcTemplate |
| Démarrer/arrêter conteneur | `DockerTaskExecutor` | Oui (`docker run/stop`) | **Garder** : waitForHealthcheck, cleanup automatique au restart |
| Créer/supprimer fichiers | `FilesystemTaskExecutor` | Oui (`rm`, `mkdir`) | **Garder** : sandboxing, validation de chemin, opérations typées |
| Requête HTTP | `HttpClientTaskExecutor` | Oui (`curl`) | **Garder** : timeout, retry, validation, intégré au RestTemplate |
| Produire message Kafka | `KafkaProducerTaskExecutor` | Oui (`kafka-console-producer`) | **Garder** : KafkaTemplate, cluster registry, delivery callback |
| Consommer messages Kafka | `KafkaConsumerTaskExecutor` | Oui (`kafka-console-consumer`) | **Garder** : consumer group, offset, cluster registry |
| Mock HTTP | `MockServerTaskExecutor` | Non (pas sans outil) | **Garder** : WireMock lifecycle, stubs JSON |
| Commande shell générique | `ShellTaskExecutor` | N/A | **Garder** : seul executor pour les cas non couverts |

**Conclusion** : Aucune duplication. Chaque executor dédié apporte des garde-fous ou une intégration qui justifient son existence. Aucune suppression recommandée.

---

## Format de l'ADR

L'ADR suit le format standard du projet (voir `.claude/knowledge/adr/`). Numéro proposé : **ADR-022**.

```
ADR-022 — Task Executor Doctrine: Dedicated vs Shell
```

Sections :
1. **Context** : 9 executors existants, question récurrente "pourquoi ne pas tout faire en shell ?"
2. **Decision** : Doctrine documented above
3. **Consequences** : ShellTaskExecutor reste le fallback, executors dédiés pour les opérations critiques
4. **Alternatives Considered** : Supprimer les executors dédiés (rejeté — perte de garde-fous)

---

## Dependances Techniques

```
Ce PDR utilise :
  PDR-010 (TaskExecutorRegistry) → déjà DONE

Ce PDR est utilisé par :
  (aucun — documentation pure)
```

---

## Criteres de Done (PDR complet)

- [ ] ADR-022 créé dans `.claude/knowledge/adr/ADR-022-task-executor-doctrine.md` (ISSUE-165)
- [ ] `package-info.java` dans `com.performance.platform.infrastructure.executor` documentant la liste des executors
- [ ] Chaque executor dédié a sa Javadoc mise à jour mentionnant ses garde-fous spécifiques
- [ ] `mvn test -pl platform-infrastructure -q` → 0 erreur (aucun changement de code, juste doc)
