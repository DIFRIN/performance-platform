package com.performance.platform.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.performance.platform.probe.grafana.GrafanaClient;
import com.performance.platform.probe.kibana.KibanaClient;
import com.performance.platform.probe.model.ProbeFingerprint;
import com.performance.platform.probe.model.ProbePoint;
import com.performance.platform.probe.model.ProbeResult;
import com.performance.platform.probe.model.ProbeSeries;
import com.performance.platform.probe.model.ProbeWindow;
import com.performance.platform.probe.model.QueryMapping;
import com.performance.platform.probe.render.SvgChart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Point d'entree du CLI autonome.
 * <p>
 * Le meme code sert de plugin plateforme ({@code panel-assertion}) : un ecart
 * de resultat entre les deux modes est un bug.
 * <p>
 * Codes de sortie : 0 succes, 1 donnees insuffisantes (INCONCLUSIVE),
 * 2 configuration/arguments, 3 erreur distante.
 */
public final class ProbeCli {

    private ProbeCli() {
    }

    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args) {
        if (args == null || args.length == 0) {
            usage();
            return 2;
        }
        String command = args[0];
        Args parsed = Args.parse(args);
        try {
            return switch (command) {
                case "catalog" -> catalog(parsed);
                case "query" -> query(parsed);
                case "verify" -> verify(parsed);
                case "render" -> render(parsed);
                case "install-plugin" -> installPlugin(parsed);
                case "help", "--help", "-h" -> {
                    usage();
                    yield 0;
                }
                default -> {
                    System.err.println("Unknown command: " + command);
                    usage();
                    yield 2;
                }
            };
        } catch (ProbeException e) {
            System.err.println("ERROR: " + e.getMessage());
            return e.exitCode();
        } catch (RuntimeException e) {
            System.err.println("ERROR: " + e.getMessage());
            return 3;
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    static int catalog(Args args) {
        ProbeConfig config = args.config();
        if (args.has("explain")) {
            config.describe().forEach((key, value) -> System.out.println(key + " = " + value));
            return 0;
        }
        if (config.isGrafana()) {
            String dashboard = args.required("dashboard");
            JsonNode node = new GrafanaClient(config).dashboard(dashboard);
            var out = Http.MAPPER.createObjectNode();
            out.put("provider", "GRAFANA");
            out.put("source", config.name());
            out.put("dashboardUid", dashboard);
            out.put("dashboardVersion", node.path("version").asInt());
            var panels = out.putArray("panels");
            collectPanels(node.path("panels"), panels);
            System.out.println(pretty(out));
            return 0;
        }
        String savedObject = args.required("saved-object");
        String[] parts = savedObject.split("/", 2);
        if (parts.length != 2) {
            throw ProbeException.config("--saved-object must use type/id, e.g. visualization/apm-error-rate");
        }
        JsonNode node = new KibanaClient(config).savedObject(parts[0], parts[1]);
        var out = Http.MAPPER.createObjectNode();
        out.put("provider", "KIBANA");
        out.put("source", config.name());
        out.put("type", parts[0]);
        out.put("id", parts[1]);
        out.set("attributes", node.path("attributes"));
        System.out.println(pretty(out));
        return 0;
    }

    static int query(Args args) {
        ProbeConfig config = args.config();
        ProbeWindow window = args.window();
        sleepFreshness(args);
        List<ProbeSeries> series;
        ProbeFingerprint fingerprint;

        if (config.isGrafana()) {
            String dashboard = args.required("dashboard");
            int panel = args.requiredInt("panel");
            var client = new GrafanaClient(config);
            long stepMillis = TimeParser.parseDurationMillis(args.optional("step"), Duration.ofSeconds(15));
            var mappings = args.mappings();
            series = client.query(dashboard, panel, mappings, window, stepMillis,
                    args.variables(), args.intOption("max-data-points", 400));
            String resolved = mappings.stream().map(QueryMapping::metric)
                    .reduce((a, b) -> a + "," + b).orElse("all targets");
            fingerprint = client.fingerprint(dashboard, panel, "",
                    window, stepMillis, resolved);
        } else {
            var client = new KibanaClient(config);
            var mappings = args.mappings();
            QueryMapping mapping = mappings.isEmpty()
                    ? new QueryMapping("A", args.optional("metric", "panel.series.A"), args.optional("unit", ""))
                    : mappings.get(0);
            KibanaClient.EsQuery esQuery = new KibanaClient.EsQuery(
                    args.required("index"),
                    args.optional("time-field", "@timestamp"),
                    args.required("field"),
                    args.optional("agg", "avg"),
                    args.has("percentile") ? Double.parseDouble(args.optional("percentile", "95")) : null,
                    args.optional("interval", "1m"),
                    mapping.metric(), mapping.unit());
            String mode = client.effectiveMode(args.optional("mode"));
            series = client.query(esQuery, window, mode);
            fingerprint = new ProbeFingerprint("kibana", config.name(), mode,
                    args.required("field"), "", esQuery.toString(), esQuery.interval(), "",
                    window.from().toString(), window.to().toString(), Instant.now());
        }

        ProbeResult result = new ProbeResult(config.name(),
                config.isGrafana() ? "GRAFANA_DS_QUERY" : "KIBANA",
                new ProbeResult.ProbeWindowDto(window.from().toString(), window.to().toString(),
                        window.resolutionMillis() + "ms"),
                fingerprint, series);

        String output = "table".equalsIgnoreCase(args.optional("format", "json"))
                ? table(series)
                : pretty(result);
        if (args.has("out")) {
            write(Path.of(args.required("out")), output);
        } else {
            System.out.println(output);
        }
        boolean empty = series.stream().allMatch(ProbeSeries::isEmpty);
        return empty ? 1 : 0;
    }

    static int verify(Args args) {
        ProbeConfig config = args.config();
        System.out.println("source " + config.name() + " (" + config.provider() + ")");
        int warnings = 0;

        if (config.isGrafana()) {
            String dashboard = args.required("dashboard");
            JsonNode node = new GrafanaClient(config).dashboard(dashboard).path("panels");
            System.out.println("OK    dashboard reached");
            int panels = countPanels(node);
            System.out.println("OK    " + panels + " panels visible");
            if (panels == 0) {
                System.out.println("WARN  no panels to query");
                warnings++;
            }
        } else {
            String savedObject = args.required("saved-object");
            String[] parts = savedObject.split("/", 2);
            new KibanaClient(config).savedObject(parts[0], parts[1]);
            System.out.println("OK    saved object reached");
            if (config.fallback() != null) {
                System.out.println("INFO  fallback configured: " + config.fallback());
            }
        }

        ProbeWindow window = new ProbeWindow(Instant.now().minus(Duration.ofMinutes(5)),
                Instant.now(), 60_000);
        try {
            List<ProbeSeries> series = probeWindow(config, args, window);
            long points = series.stream().mapToLong(one -> one.points().size()).sum();
            if (points == 0) {
                System.out.println("WARN  no data in the last 5 minutes: a report would be INCONCLUSIVE");
                warnings++;
            } else {
                System.out.println("OK    " + points + " points read in the last 5 minutes");
            }
        } catch (ProbeException e) {
            System.out.println("WARN  query check failed: " + e.getMessage());
            warnings++;
        }
        System.out.println(warnings == 0 ? "RESULT OK" : "RESULT WARN (" + warnings + ")");
        return warnings == 0 ? 0 : 1;
    }

    private static List<ProbeSeries> probeWindow(ProbeConfig config, Args args, ProbeWindow window) {
        if (config.isGrafana()) {
            return new GrafanaClient(config).query(args.required("dashboard"),
                    args.requiredInt("panel"), args.mappings(), window, 15_000,
                    args.variables(), 100);
        }
        var client = new KibanaClient(config);
        var query = new KibanaClient.EsQuery(args.required("index"), "@timestamp",
                args.required("field"), args.optional("agg", "avg"), 95.0, "1m",
                "probe", "");
        return client.query(query, window, client.effectiveMode(args.optional("mode")));
    }

    static int render(Args args) {
        Path input = Path.of(args.required("in"));
        try {
            var result = Http.MAPPER.readValue(input.toFile(), ProbeResult.class);
            String svg = SvgChart.render(args.optional("title", "Observability panel"),
                    result.observations());
            String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>"
                    + args.optional("title", "Observability panel")
                    + "</title></head><body>" + svg + "</body></html>";
            write(Path.of(args.required("out")), html);
            System.out.println("written " + args.optional("out", "panel.html"));
            return 0;
        } catch (IOException e) {
            throw ProbeException.config("Cannot read " + input + ": " + e.getMessage());
        }
    }

    static int installPlugin(Args args) {
        Path directory = Path.of(args.optional("dir", "./plugins"));
        try {
            Files.createDirectories(directory);
            Path self = Path.of(ProbeCli.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI());
            if (!Files.isRegularFile(self)) {
                throw ProbeException.config("Cannot install the plugin from an exploded classpath; "
                        + "run the packaged JAR instead");
            }
            Path target = directory.resolve("observability-probe.jar");
            Files.copy(self, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("plugin installed: " + target.toAbsolutePath());
            if (args.has("print-config")) {
                System.out.println();
                System.out.println("platform:");
                System.out.println("  plugins:");
                System.out.println("    enabled: true");
                System.out.println("    dir: " + directory.toAbsolutePath());
                System.out.println("  metric-sources:");
                System.out.println("    " + args.optional("source", "grafana-prod") + ":");
                System.out.println("      provider: GRAFANA");
                System.out.println("      url: ${GRAFANA_URL}");
                System.out.println("      token: ${GRAFANA_TOKEN}");
                System.out.println();
                System.out.println("agent:");
                System.out.println("  supported-tasks: panel-assertion");
            }
            return 0;
        } catch (IOException | java.net.URISyntaxException e) {
            throw ProbeException.config("Cannot install plugin: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void sleepFreshness(Args args) {
        Duration delay = TimeParser.parseDuration(args.optional("freshness-delay"), Duration.ZERO);
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void collectPanels(JsonNode panels, com.fasterxml.jackson.databind.node.ArrayNode out) {
        if (panels == null || !panels.isArray()) {
            return;
        }
        for (JsonNode panel : panels) {
            if (panel.has("targets") || panel.has("panels")) {
                var entry = out.addObject();
                entry.put("panelId", panel.path("id").asInt());
                entry.put("title", panel.path("title").asText(""));
                entry.put("datasourceUid", panel.path("datasource").path("uid").asText(""));
                var queries = entry.putArray("queries");
                for (JsonNode target : panel.path("targets")) {
                    var query = queries.addObject();
                    query.put("refId", target.path("refId").asText("A"));
                    query.put("expr", target.path("expr").asText(target.path("query").asText("")));
                }
                collectPanels(panel.path("panels"), out);
            }
        }
    }

    private static int countPanels(JsonNode panels) {
        if (panels == null || !panels.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode panel : panels) {
            count += panel.has("targets") ? 1 : 0;
            count += countPanels(panel.path("panels"));
        }
        return count;
    }

    private static String table(List<ProbeSeries> series) {
        StringBuilder text = new StringBuilder();
        text.append(String.format("%-32s %-12s %8s %12s %12s %12s%n",
                "metric", "unit", "points", "min", "max", "last"));
        for (ProbeSeries one : series) {
            double min = one.points().stream().mapToDouble(ProbePoint::value).min().orElse(Double.NaN);
            double max = one.points().stream().mapToDouble(ProbePoint::value).max().orElse(Double.NaN);
            double last = one.points().isEmpty() ? Double.NaN
                    : one.points().get(one.points().size() - 1).value();
            text.append(String.format("%-32s %-12s %8d %12.2f %12.2f %12.2f%n",
                    one.metric(), one.unit(), one.points().size(), min, max, last));
        }
        return text.toString();
    }

    private static String pretty(Object value) {
        try {
            return Http.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private static void write(Path path, String content) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ProbeException.config("Cannot write " + path + ": " + e.getMessage());
        }
    }

    private static void usage() {
        System.out.println("""
                observability-probe <command> [options]

                commands:
                  catalog         list panels/queries of a dashboard or saved object
                  query           read metrics for a time window (JSON or table)
                  verify          check reachability, backend datasource and coverage
                  render          rebuild an SVG/HTML chart from a query output
                  install-plugin  copy the plugin JAR and print platform configuration

                common options:
                  --source NAME --config probe.yaml
                  --from ISO|now-30m --to ISO|now --margin-before 1m --margin-after 2m
                  --freshness-delay 30s --map refId:metricId:unit (repeatable)
                  --var name=value (repeatable) --format json|table --out FILE

                grafana:
                  --dashboard UID --panel ID --step 15s --max-data-points 400

                kibana:
                  --saved-object type/id --index INDEX --field FIELD
                  --agg avg|max|min|sum|count|percentiles --percentile 95
                  --interval 1m --mode ES_DIRECT|CONSOLE_PROXY

                env: GRAFANA_URL GRAFANA_TOKEN GRAFANA_ORG_ID
                     KIBANA_URL KIBANA_API_KEY ES_URL ES_API_KEY
                     PROBE_KIBANA_MODE PROBE_KIBANA_FALLBACK PROBE_CONFIG

                exit: 0 success, 1 insufficient data, 2 configuration, 3 remote error
                """);
    }

    // ------------------------------------------------------------------

    /** Analyse minimaliste des options CLI. */
    static final class Args {

        private final Map<String, List<String>> values = new LinkedHashMap<>();

        static Args parse(String[] args) {
            Args parsed = new Args();
            List<String> current = null;
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("--")) {
                    String key = arg.substring(2);
                    if (key.contains("=")) {
                        String[] parts = key.split("=", 2);
                        parsed.values.computeIfAbsent(parts[0], k -> new ArrayList<>()).add(parts[1]);
                        current = null;
                    } else {
                        current = parsed.values.computeIfAbsent(key, k -> new ArrayList<>());
                    }
                } else if (current != null) {
                    current.add(arg);
                }
            }
            return parsed;
        }

        boolean has(String key) {
            return values.containsKey(key);
        }

        String optional(String key) {
            return optional(key, null);
        }

        String optional(String key, String defaultValue) {
            List<String> list = values.get(key);
            return list == null || list.isEmpty() ? defaultValue : list.get(list.size() - 1);
        }

        String required(String key) {
            String value = optional(key, null);
            if (value == null || value.isBlank()) {
                throw ProbeException.config("Missing required option --" + key);
            }
            return value;
        }

        int requiredInt(String key) {
            try {
                return Integer.parseInt(required(key));
            } catch (NumberFormatException e) {
                throw ProbeException.config("--" + key + " must be an integer");
            }
        }

        int intOption(String key, int defaultValue) {
            String value = optional(key, null);
            return value == null ? defaultValue : Integer.parseInt(value);
        }

        ProbeConfig config() {
            return ProbeConfig.load(optional("source", "default"),
                    optional("config", null) == null ? null : Path.of(optional("config", null)));
        }

        ProbeWindow window() {
            return TimeParser.window(required("from"), optional("to", "now"),
                    optional("step", optional("interval", "15s")));
        }

        List<QueryMapping> mappings() {
            List<String> raw = values.get("map");
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            return raw.stream().map(QueryMapping::parse).toList();
        }

        Map<String, String> variables() {
            List<String> raw = values.get("var");
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            Map<String, String> variables = new LinkedHashMap<>();
            for (String entry : raw) {
                String[] parts = entry.split("=", 2);
                if (parts.length != 2) {
                    throw ProbeException.config("--var must use name=value, got: " + entry);
                }
                variables.put(parts[0], parts[1]);
            }
            return variables;
        }
    }
}
