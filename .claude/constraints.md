# Constraints — Performance Engineering Platform

> Ces contraintes sont NON NÉGOCIABLES. Elles guident chaque décision d'implémentation.
> En cas de conflit avec une spec, les contraintes l'emportent.

---

## Contraintes Fonctionnelles

### CF-01 — Artefact Unique
Un seul fichier JAR. Le mode (LOCAL / DISTRIBUTED / ORCHESTRATOR / AGENT) est déterminé par variables
d'environnement (prioritaires) ou configuration YAML (défaut). Voir ADR-006.
Aucun profil Maven différent, aucune classe main différente.

### CF-02 — Immutabilité du Contexte
`ExecutionContext` est créé une fois au démarrage d'une exécution et ne mute jamais.
Les tâches ajoutent des résultats via une copie (copy-on-write ou builder pattern).
Jamais de `setXxx()` sur les objets du domaine.

### CF-03 — Pas de Code Conditionnel sur le Type de Transport
Le type de transport actif est injecté via Spring. Aucun `if (type == KAFKA)` dans
le code métier. Uniquement `@ConditionalOnProperty` ou `@Profile`.
La propriété est alimentée par `TRANSPORT_TYPE` env var (prioritaire) ou `transport.type` yaml.

### CF-04 — Plugin sans Recompilation
Ajouter un nouveau `TaskExecutor` doit être possible sans modifier le code existant.
Mécanisme : Spring bean auto-découverte + `TaskExecutorRegistry`.

### CF-05 — Scénario YAML = Source de Vérité
Le fichier YAML est validé à la soumission. Une exécution ne peut démarrer
avec un scénario invalide. Retourner des erreurs de validation détaillées (champ + message).

### CF-06 — Plugin JAR : Démarrage Seulement
Les JARs du répertoire `platform.plugins.dir` sont chargés **une seule fois** au démarrage.
Pas de chargement à chaud. Pas de rechargement sans redémarrage de la JVM.
Un JAR invalide génère un warning et est ignoré — pas un crash.

### CF-07 — Annotations Obligatoires pour les Plugins TaskExecutor
Tout `TaskExecutor` (interne ou externe) doit être annoté avec exactement une des
trois annotations : `@Preparation`, `@Injection`, ou `@Assertion`.
Le `name` de l'annotation est la clé de résolution dans le DSL YAML.
Deux executors avec le même `name` et le même type : le plugin externe prime, warning loggé.

### CF-08 — platform-plugin-api : Interface Stable
Le module `platform-plugin-api` est l'API publique de la plateforme pour les développeurs
de plugins. Ses interfaces (`TaskExecutor`, `ExecutionContext`, `TaskResult`) et
annotations (`@Preparation`, `@Injection`, `@Assertion`) ne peuvent pas changer
sans un ADR et un changement de version majeure.

---

## Contraintes Non-Fonctionnelles

### CNF-01 — Scalabilité
- Support de 10 000+ tâches par campagne
- Support de 100+ agents simultanés
- Utiliser Virtual Threads (Project Loom) pour tout I/O bloquant

### CNF-02 — Fiabilité
- Retry configurable par tâche (max attempts + backoff)
- Idempotence : re-soumettre une tâche avec le même `TaskId` ne crée pas de doublon
- Checkpointing : état de l'exécution persisté en base après chaque phase
- Timeout configurable par tâche

### CNF-03 — Sécurité
- Toutes les communications inter-composants : TLS minimum, mTLS supporté
- API REST : OAuth2 / JWT
- Secrets : jamais en clair dans les configs (Spring Cloud Config ou K8s Secrets)

### CNF-04 — Observabilité (Obligatoire)
Toute exécution doit exposer :
- Métriques Micrometer : `execution_duration`, `task_duration`, `task_failures_total`
- Traces OpenTelemetry sur chaque tâche et chaque phase
- Logs structurés JSON avec : `executionId`, `scenarioId`, `taskId`, `agentId`, `phase`

### CNF-05 — Performance Propre
La plateforme elle-même ne doit pas consommer plus de :
- 512MB RAM au repos (mode AGENT)
- 2GB RAM sous charge (mode ORCHESTRATOR, 100 agents)
- Démarrage en moins de 10 secondes

---

## Contraintes de Code

### CC-01 — Coverage
- Domaine (`platform-domain`) : minimum 90% line coverage
- Application (`platform-application`) : minimum 80% line coverage
- Infrastructure : minimum 60% (tests d'intégration avec Testcontainers)

### CC-02 — Taille des Classes
- Aucune classe > 300 lignes (hors classes générées)
- Aucune méthode > 40 lignes
- Exception documentée par commentaire si dépassement justifié

### CC-03 — Dépendances
- Aucune dépendance cyclique entre modules Maven
- Aucune dépendance directe entre modules Spring Modulith
- Toute nouvelle dépendance Maven : commentaire dans le pom.xml parent avec justification

### CC-04 — Backwards Compatibility
- Les interfaces publiques (`TaskExecutor`, `ExecutionTransport`, `ReportPublisher`)
  sont versionnées. Toute modification breaking : nouvel ADR obligatoire.

---

## Contraintes de Déploiement

### CD-01 — Image Docker
- Base : `eclipse-temurin:25-jre-alpine` (minimal)
- User non-root dans le container
- Health check exposé sur `/actuator/health`
- Taille image < 300MB

### CD-02 — Kubernetes
- Stateless pour les agents (StatefulSet pour l'orchestrateur si besoin de volume)
- HPA sur CPU/memory pour les agents
- ConfigMap pour la config applicative, Secret pour les credentials
- Readiness et Liveness probes obligatoires

### CD-03 — Versions
- Java 25 LTS uniquement
- Spring Boot 4.x (pas de downgrade vers 3.x ou antérieur)
- Gatling 3.x Java DSL (pas de Scala DSL)
