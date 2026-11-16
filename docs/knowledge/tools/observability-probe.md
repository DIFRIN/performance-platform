# Observability probe — annex tool

Outil **hors reactor** (`tools/observability-probe`) qui expose les mêmes
adaptateurs que la plateforme sous deux formes :

1. un **plugin JAR** pour la plateforme (`@Assertion("panel-assertion")`,
   `PanelAssertionExecutor`), déposé dans `platform.plugins.dir` ;
2. un **CLI autonome** utilisable sans plateforme (diagnostic, génération de
   fixtures, smoke test CI, capture d'observations pour un rapport).

Références : [ADR-050](../adr/ADR-050-external-observability-metrics.md),
[Spec 10](../specs/10-observability-assertions-and-evidence.md).

> Le code est le même dans les deux modes : le CLI et le plugin appellent les
> mêmes `GrafanaClient` / `KibanaClient`. Un comportement différent entre
> le rapport et l'outil est donc un bug, pas une divergence attendue.

---

## 1. Build

```bash
# depuis la racine du dépôt
cd tools/observability-probe
mvn -q clean package            # produit target/observability-probe.jar (shaded, runnable)
```

Le module n'est **pas** membre du reactor principal : `mvn install` à la racine ne
le construit pas et la plateforme n'hérite d'aucune dépendance Grafana/Kibana.

---

## 2. Configuration

Deux sources possibles, dans cet ordre de priorité (aligné sur ADR-006) :

1. variables d'environnement / options CLI ;
2. fichier `probe.yaml` (`--config probe.yaml`).

```yaml
# probe.yaml
sources:
  grafana-prod:
    provider: GRAFANA
    url: https://grafana.example.com
    token: ${GRAFANA_TOKEN}
    orgId: 1
    timeout: 30s

  kibana-prod:
    provider: KIBANA
    mode: ES_DIRECT              # ES_DIRECT | CONSOLE_PROXY
    kibanaUrl: https://kibana.example.com
    kibanaApiKey: ${KIBANA_API_KEY}
    elasticsearchUrl: https://es.example.com
    elasticsearchApiKey: ${ES_API_KEY}
    fallback: CONSOLE_PROXY      # utilisé si ES injoignable, tracé dans le fingerprint
```

Variables reconnues : `GRAFANA_URL`, `GRAFANA_TOKEN`, `GRAFANA_ORG_ID`,
`KIBANA_URL`, `KIBANA_API_KEY`, `ES_URL`, `ES_API_KEY`, `PROBE_CONFIG`.

Aucun secret n'est accepté en argument positionnel (il apparaîtrait dans
l'historique du shell) : utiliser l'environnement ou le fichier.

---

## 3. Commandes

```
observability-probe <command> [options]

commandes :
  catalog         liste les dashboards/panels et leurs requêtes résolues
  query           exécute les requêtes et produit des Observation[] (JSON)
  verify          vérifie la fraîcheur, la couverture et la joignabilité
  render          produit un SVG/HTML à partir d'observations déjà capturées
  install-plugin  copie le JAR dans platform.plugins.dir et affiche le reste à faire
```

Codes de sortie (mêmes conventions que le CLI plateforme, ADR-021) :

| Code | Sens |
|---|---|
| `0` | succès |
| `1` | requête exécutée mais données insuffisantes (`INCONCLUSIVE`) |
| `2` | configuration/arguments invalides, authentification refusée |
| `3` | erreur réseau/plateforme (timeout, 5xx, datasource non backend) |

### 3.1 `catalog` — inspecter un dashboard

```bash
export GRAFANA_URL=https://grafana.example.com
export GRAFANA_TOKEN=glsa_xxx

java -jar observability-probe.jar catalog \
  --source grafana-prod \
  --dashboard api-slo
```

Sortie (extrait) :

```json
{
  "provider": "GRAFANA",
  "dashboardUid": "api-slo",
  "dashboardVersion": 14,
  "panels": [
    {
      "panelId": 12,
      "title": "Latency p95 / RPS",
      "datasourceUid": "prom-main",
      "variables": { "service": "customer-api", "env": "prod" },
      "queries": [
        { "refId": "A", "expr": "histogram_quantile(0.95, sum by (le) (rate(http_request_duration_seconds_bucket[2m])))" },
        { "refId": "B", "expr": "sum(rate(http_requests_total[2m]))" }
      ]
    }
  ]
}
```

Kibana :

```bash
java -jar observability-probe.jar catalog \
  --source kibana-prod \
  --saved-object visualization/apm-error-rate
```

### 3.2 `query` — récupérer les métriques

Grafana, fenêtre explicite :

```bash
java -jar observability-probe.jar query \
  --source grafana-prod \
  --dashboard api-slo --panel 12 \
  --from 2026-11-13T09:00:00Z --to 2026-11-13T09:20:00Z \
  --margin-after 2m --freshness-delay 30s \
  --map refId=A:injection.latency.p95:ms \
  --map refId=B:injection.throughput:req_per_sec \
  --step 15s \
  --out observations.json
```

Fenêtre relative (diagnostic ad hoc) :

```bash
java -jar observability-probe.jar query \
  --source grafana-prod --dashboard api-slo --panel 12 \
  --from now-30m --to now \
  --map refId=A:injection.latency.p95:ms \
  --format table
```

Kibana `ES_DIRECT` :

```bash
java -jar observability-probe.jar query \
  --source kibana-prod \
  --saved-object visualization/apm-error-rate \
  --from 2026-11-13T09:00:00Z --to 2026-11-13T09:20:00Z \
  --interval 1m \
  --map refId=rate:panel.series.error_rate:percent \
  --mode ES_DIRECT
```

Kibana `CONSOLE_PROXY` (mêmes options, canal différent) :

```bash
java -jar observability-probe.jar query \
  --source kibana-prod \
  --saved-object visualization/apm-error-rate \
  --from 2026-11-13T09:00:00Z --to 2026-11-13T09:20:00Z \
  --interval 1m \
  --map refId=rate:panel.series.error_rate:percent \
  --mode CONSOLE_PROXY
```

Sortie = contrat `Observation` de la spec 10 :

```json
{
  "source": "grafana-prod",
  "accessMode": "GRAFANA_DS_QUERY",
  "window": { "from": "2026-11-13T09:00:00Z", "to": "2026-11-13T09:22:00Z", "resolution": "15s" },
  "fingerprint": {
    "dashboardUid": "api-slo",
    "dashboardVersion": 14,
    "panelId": 12,
    "datasourceUid": "prom-main",
    "resolvedQuery": "histogram_quantile(0.95, ...)",
    "fetchedAt": "2026-11-13T09:24:11Z"
  },
  "observations": [
    { "metric": "injection.latency.p95", "unit": "ms", "points": [[1763024400000, 412.0], [1763024415000, 438.0]] },
    { "metric": "injection.throughput", "unit": "req_per_sec", "points": [[1763024400000, 118.4], [1763024415000, 121.0]] }
  ]
}
```

Options principales :

| Option | Rôle | Défaut |
|---|---|---|
| `--from` / `--to` | ISO-8601 ou relatif `now-15m` | requis |
| `--margin-before` / `--margin-after` | marge de fenêtre | `0` |
| `--freshness-delay` | attente avant lecture (scrape/refresh) | `0` |
| `--step` / `--interval` | résolution (Grafana / ES) | datasource |
| `--max-data-points` | plafond Grafana | `400` |
| `--mode` | `ES_DIRECT` \| `CONSOLE_PROXY` (Kibana) | config |
| `--map refId:metricId:unit` | mapping requête → `MetricId` canonique | `panel.series.<refId>` |
| `--exclude-empty` | ignore les séries sans point | `false` |
| `--out` | fichier JSON de sortie | stdout |

### 3.3 `verify` — avant de faire confiance

```bash
java -jar observability-probe.jar verify --source grafana-prod --dashboard api-slo
```

Vérifie : joignabilité, version de datasource, plugin backend, variables
résolues, nombre de points minimal, trous, skew d'horloge. Sortie :

```
OK    datasource prom-main (backend)          112 ms
OK    dashboard api-slo v14                   14 panels
WARN  panel 12 variable $env non liée         -> fournir --var env=prod
OK    clock skew                              +0.4 s
```

`WARN` ⇒ code `1` : le rapport produirait `INCONCLUSIVE` sur ce panneau.

### 3.4 `render` — rapport sans plateforme

```bash
java -jar observability-probe.jar render \
  --in observations.json \
  --title "API — p95 / débit" \
  --out panel.html
```

Produit le même SVG déterministe que le renderer de rapport (même code de
tracé), utile pour vérifier l'aspect avant de générer un rapport.

### 3.5 `install-plugin` — intégration plateforme

```bash
java -jar observability-probe.jar install-plugin \
  --dir /opt/performance-platform/plugins \
  --print-config
```

Affiche la configuration à reporter côté plateforme. Le plugin lit les mêmes
variables d'environnement / `probe.yaml` que le CLI (aucune clé supplémentaire
sous `platform:`) :

```yaml
platform:
  plugins:
    enabled: true
    dir: /opt/performance-platform/plugins

agent:                       # en DISTRIBUTED / AGENT
  supported-tasks: panel-assertion
```

```bash
# sur l'agent qui exécute panel-assertion
export GRAFANA_URL=https://grafana.example
export GRAFANA_TOKEN=…            # jamais dans le scénario ni le rapport
```

`panel-assertion` devient alors utilisable dans un scénario comme n'importe
quelle assertion (spec 10 §3.3), le paramètre `source` sélectionnant le nom
logique (défaut `default`).

---

## 4. Docker

```bash
# construire d'abord le JAR (voir §1)
docker build -t observability-probe:local tools/observability-probe

docker run --rm \
  -e GRAFANA_URL -e GRAFANA_TOKEN \
  -v "$PWD/observations.json:/out/observations.json" \
  observability-probe:local \
  query --source grafana-prod --dashboard api-slo --panel 12 \
        --from now-30m --to now \
        --map refId=A:injection.latency.p95:ms \
        --out /out/observations.json
```

Image construite depuis `tools/observability-probe/Dockerfile` (base JRE 25,
copie du JAR shaded déjà construit ; pas de build Maven dans l'image).

---

## 5. Usage CI

```yaml
- name: Observability smoke
  run: |
    java -jar tools/observability-probe/target/observability-probe.jar verify \
      --source grafana-staging --dashboard api-slo
  env:
    GRAFANA_URL: ${{ secrets.GRAFANA_URL }}
    GRAFANA_TOKEN: ${{ secrets.GRAFANA_TOKEN }}
```

- `verify` échoue (code 1/2/3) si le dashboard a bougé, si la datasource n'est
  plus backend, ou si la fenêtre n'a pas assez de points : la CI détecte la
  dérive **avant** qu'un rapport ne produise un `INCONCLUSIVE` silencieux.
- `query --out` permet d'archiver un jeu d'observations comme fixture de test.

---

## 6. Dépannage

| Symptôme | Cause probable | Action |
|---|---|---|
| `400 datasource not found` | `datasource.uid` absent du target | `catalog --panel` pour lire l'uid réel |
| `400 query failed` Grafana | plugin datasource *frontend* uniquement | requêter la datasource sous-jacente via `/api/datasources/proxy` |
| valeurs décalées | variable de template non liée | `--var service=customer-api` |
| série vide | fenêtre hors rétention | ajuster `--from`, vérifier la rétention Prometheus/ES |
| `p95 = 0` | pas de trafic sur la fenêtre | `verify` le signale (`INCONCLUSIVE`, jamais `PASSED`) |
| Kibana `401` en proxy | header `kbn-xsrf` absent | l'outil l'ajoute ; vérifier l'API key |
| Kibana `403` sur `_search` | credentials ES insuffisants | passer `--mode CONSOLE_PROXY` ou fournir `ES_API_KEY` |
| empreinte différente entre deux runs | dashboard modifié ou fallback de mode | comparer `dashboardVersion` et `accessMode` |

---

## 7. Garanties

- **Aucun secret** dans les sorties JSON ni dans les empreintes.
- **Mêmes règles** que la plateforme : `EQ` flottant refusé sans tolérance,
  `COVERAGE` vérifiée avant comparaison, `INCONCLUSIVE` plutôt qu'un faux `PASSED`.
- **Reproductibilité** : une empreinte suffit à rejouer la requête
  (`query --fingerprint observations.json`).
- **Isolation** : le module n'ajoute aucune dépendance au reactor plateforme ;
  sa suppression ne casse aucun build.
