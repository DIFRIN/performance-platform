# Spec 07 — Assertion Framework

**Module** : `platform-assertion` (pipeline framework-free) + `platform-assertion-adapters` (familles d'execution)
**Dépend de** : `platform-domain`, `platform-use-cases`, `platform-plugin-api`

> Modèle unique (ADR-049) : une assertion **mesure** (`Observation`), **attend**
> (`Expectation`) et **conclut** (`Check`). Le pipeline est partagé par toutes
> les familles ; aucun adapter ne choisit le statut. Les sources externes
> (panneaux Grafana/Kibana) et les critères temporels sont décrits dans
> [Spec 10](10-observability-assertions-and-evidence.md).

---

## 1. Objectif

Évaluer des assertions en lisant l'`ExecutionContext` (sorties d'injection,
de préparation, de mock) ou des systèmes externes (datasource, API
d'administration d'un mock, panneau), puis produire un `AssertionSummary`
contenant un ou plusieurs `Check` et, si fourni, le SLI/SLO textuel du scénario.

Depuis ADR-029/ADR-030 il n'existe **plus de registre d'assertions séparé** :
`AssertionExecutor extends TaskExecutor` et les implémentations sont collectées
par le `TaskExecutorRegistry` commun. Le moteur traite les assertions comme des
tâches standard (chemin unique LOCAL/DISTRIBUTED).

---

## 2. Contrat

```java
public interface AssertionExecutor extends TaskExecutor {

    AssertionSummary evaluate(ExecutionContext context, StepDefinition step);

    String getSupportedAssertionName();

    @Override
    default String getSupportedTaskName() { return getSupportedAssertionName(); }

    @Override
    default TaskResult execute(ExecutionContext context, StepDefinition step) {
        return AssertionSummaries.toTaskResult(evaluate(context, step), getSupportedAssertionName());
    }
}
```

- `evaluate(...)` ne lance **jamais** d'exception métier : une erreur produit un
  `Check` en statut `ERROR`.
- La conversion `AssertionSummary → TaskResult` est **unique**
  (`AssertionSummaries`), y compris le mapping des statuts :

| Statut | `TaskStatus` | Effet sur le rapport |
|---|---|---|
| `PASSED` | `SUCCESS` | succès |
| `FAILED` | `FAILED` | échec (critique si `Severity.CRITICAL`) |
| `SKIPPED` | `SKIPPED` | neutre |
| `INCONCLUSIVE` | `SUCCESS` | limitation, jamais un succès |
| `ERROR` | `FAILED` | anomalie technique |

---

## 3. Modèle

```java
record MetricId(String namespace, String name) {}

record MetricDefinition(MetricId id, MetricKind kind, String unit,
                        Reduction reduction, String description) {}

record Observation(MetricId metric, double value, String unit, TimeWindow window,
                   String source, QueryFingerprint fingerprint,
                   Map<String, Object> attributes) {}

record Expectation(AssertionOperator operator, double threshold,
                   double tolerance, Severity severity) {}

record Check(MetricId metric, Observation observed, Expectation expected,
             AssertionStatus status, String intent, String rationale) {}

record SloStatement(String title, String sli, String slo, String rationale) {}

record AssertionSummary(TaskId assertionId, AssertionStatus verdict, String description,
                        List<AssertionSample> history,
                        Duration evaluationDuration, Instant evaluatedAt,
                        AssertionFamily family, SloStatement slo,
                        List<Check> checks, Rollup rollup) {}
```

`AssertionStatus` : `PASSED`, `FAILED`, `SKIPPED`, `INCONCLUSIVE`, `ERROR`.
`AssertionOperator` : `LT`, `LTE`, `GT`, `GTE`, `EQ`, `NEQ`.
`AssertionFamily` : `PERFORMANCE_THRESHOLDS`, `DIRECT`, `PANEL`, `CUSTOM`.
`Rollup` : `ALL` (défaut), `ANY`, `QUORUM(n)`.

Règles portées par le domaine :

- `EQ`/`NEQ` sur une métrique continue (`GAUGE`, `RATIO`, `QUANTILE`,
  `DURATION`) exige une **tolérance strictement positive** ;
- les quantiles ne sont **jamais** agrégés par moyenne : `Reduction.MERGE` somme les
  histogrammes bucket par bucket puis calcule le quantile sur la distribution fusionnée
  (percentile global, ADR-068) ; `MAX` = pire agent (`injection.latency.pNN.worst-agent`),
  `SUM` pour les compteurs, `ALL` pour les états ;
- seules les fenêtres `measured` (hors warm-up) entrent dans les métriques ; débit et
  arrivées utilisent la fenêtre commune à tous les agents contributeurs ;
- `Rollup.combine` renvoie `INCONCLUSIVE` dès qu'une mesure manque : un succès
  ne peut pas être déduit d'une absence de données.

Le catalogue canonique (`MetricCatalog`) porte les 24 métriques et normalise les
unités (`%`, `req/s`, `requests`…). Les noms canoniques (`injection.latency.p95`,
`kafka.messages.consumed`, …) sont les seuls acceptés : aucun alias n'est résolu.

---

## 4. Familles

### 4.1 `performance-thresholds` — SLO normalisés

```yaml
- id: api-slo
  task: performance-thresholds
  phase: ASSERTION
  description:
    title: API latency
    sli: temps de réponse HTTP des POST /api/events
    slo: p95 <= 500 ms, p99 <= 900 ms, erreurs <= 1 %, débit >= 100 req/s
    rationale: contrat client 2026-Q4
  parameters:
    latency: { p50: 200, p95: 500, p99: 900, unit: ms, severity: WARNING }
    errorRate: { max: 1.0, unit: percent, severity: CRITICAL }
    throughput: { min: 100, unit: req_per_sec, severity: WARNING }
```

Un seul bloc produit cinq `Check` ; les percentiles non contraints sortent en
`SKIPPED` documenté, afin que le rapport expose toujours la structure p50/p95/p99.
Un paramètre `checks:` explicite permet d'ajouter n'importe quelle métrique
d'injection avec opérateur/seuil/tolérance.

### 4.2 `direct-assertion` — mesure directe

```yaml
- id: orders-processed
  task: direct-assertion
  phase: ASSERTION
  parameters:
    metric: database.updates          # database.scalar, filesystem.exists, ...
    datasource: customer-db
    query: "UPDATE orders SET status='DONE' WHERE status='CREATED'"
    operator: GTE
    threshold: 1000
    severity: CRITICAL
```

Métriques résolues par `DirectMeasurementResolver` :

| Namespace | Métriques | Paramètres |
|---|---|---|
| `database` | `database.scalar`, `database.updates` | `datasource` (nom de `platform.datasources`, défaut : `default`), `query` |
| `filesystem` | `filesystem.exists`, `.size`, `.checksum` | `path`, `expect`, `checksum` |
| `http` | `http.requests.matched`, `.unmatched` | `target` (cible nommée), `mockUrl` + `endpoint`/`method`, ou `ref` |
| `kafka` | `kafka.messages.consumed`, `.produced`, `.consumer.lag` | `ref` (+ `output`) |
| `injection` | `injection.latency.*` (dont `.worst-agent`), `injection.error.rate`, `injection.throughput`, `injection.arrival.{planned,achieved,deviation}`, `injection.start.skew`, `injection.generator.{cpu,gc.pause}` | `ref` |
| `integrity` | `injection.arrival.deviation`, `injection.agent.loss`, `injection.run.incomplete`, `injection.generator.cpu` | automatique (ADR-069), voir §4.5 |

Les métriques `STATE` (`filesystem.exists`, `filesystem.checksum`) comparent un
état converti en `1.0`/`0.0` : plus de branchement `EXISTS`/`CHECKSUM` dans un
executor.

### 4.3 `panel-assertion` — métriques de dashboard

Fournie par l'annexe [observability-probe](../tools/observability-probe.md)
(plugin JAR). Voir [Spec 10](10-observability-assertions-and-evidence.md) §3.3.

### 4.5 `integrity` — validité de l'expérience (ADR-069)

Famille produite automatiquement pour chaque étape d'injection (`IntegrityCheckFactory`,
fonction pure), sans déclaration. Contrôles : déviation d'arrivée (défaut 5 %, `CRITICAL`),
perte d'agent accordé sans résultat (`CRITICAL`), run incomplet (`CRITICAL`), saturation du
générateur (CPU > 90 %, `WARNING` seul). Tolérances réglables par étape
(`integrity.maxArrivalDeviation`, `integrity.maxGeneratorCpu`), jamais désactivables ;
la valeur utilisée est rapportée. Simulation opaque : déviation `INCONCLUSIVE`. Un
contrôle `CRITICAL` en échec donne le verdict `INVALID`.

### 4.4 Critères temporels

`COVERAGE`, `REACH`, `SUSTAINED`, `SPREAD`, `TREND`, `RECOVERY`, `MONOTONIC`
s'appliquent à une `SeriesObservation`. `COVERAGE` est vérifiée en premier :
une série trop courte ou trouée produit `INCONCLUSIVE`. Voir Spec 10 §4.

---

## 5. Contribution au verdict

Deux niveaux, une seule source :

1. **Verdict d'exécution** (`ExecutionEngine`) : toute tâche `FAILED` rend le
   verdict `FAILED` ; un `SKIPPED` donne `WARNING`. Un `Check` `INCONCLUSIVE`
   n'échoue pas l'exécution (la tâche reste `SUCCESS`) mais limite le rapport.
2. **Verdict de rapport** (`PerformanceAssessmentBuilder`) : `FAILED` si un
   check `CRITICAL` échoue ou en cas d'échec technique ; sinon `WARNING` si un
   check est `INCONCLUSIVE`/`SKIPPED` ou si une limitation existe ; sinon
   `SUCCESS`.
3. **`INVALID`** : un contrôle d'intégrité critique en échec prime sur tout :
   `INVALID` > `FAILED` > `WARNING` > `SUCCESS`. Une étape en échec avant toute charge
   (plan rejeté, `minAgents` non atteint) reste `FAILED`.

---

## 6. Découverte et exécution

- Découverte : beans Spring annotés `@Assertion(name="…")`, collectés par
  `TaskExecutorRegistry` (et par `PluginRegistry` pour les plugins externes).
- Chargement plugin : `platform.plugins.enabled=true` + JAR dans
  `platform.plugins.dir` (constructeur no-arg).
- Exécution : chemin unique `TransportStepDispatcher` (moteur et transport uniques, ADR-059) ;
  aucune branche spécifique aux assertions.
- En DISTRIBUTED, l'agent déclare le nom d'assertion utilisé dans
  `agent.supported-tasks` (`direct-assertion`, `performance-thresholds`,
  `panel-assertion`).

---

## 7. Validation au parsing

`DefaultScenarioValidator` rejette au chargement du scénario (HTTP 400) :

- métrique inconnue (`MetricCatalog.find` vide) ;
- métrique continue avec `operator: EQ`/`NEQ` et `tolerance` absente ou nulle ;
- `direct-assertion` sans `metric`, ou sans `operator`/`threshold` pour une
  métrique non `STATE` ;
- `performance-thresholds` sans `latency`, `errorRate`, `throughput` ni `checks`.

Un bloc `description` absent produit un **avertissement**, pas une erreur : le
contexte SLI/SLO est recommandé pour le rapport.

