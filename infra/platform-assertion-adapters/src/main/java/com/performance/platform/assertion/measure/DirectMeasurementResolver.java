package com.performance.platform.assertion.measure;

import com.performance.platform.plugin.resource.Endpoint;
import com.performance.platform.plugin.resource.EndpointResolver;
import com.performance.platform.plugin.resource.NamedResourceResolver;
import com.performance.platform.domain.assertion.MetricDefinition;
import com.performance.platform.domain.assertion.Observation;
import com.performance.platform.domain.assertion.QueryFingerprint;
import com.performance.platform.domain.assertion.TimeWindow;
import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.task.TaskResult;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Produit une {@link Observation} pour les metriques "directes" :
 * base de donnees, systeme de fichiers et sorties de taches deja executees.
 * <p>
 * Ne juge jamais : la comparaison est faite par {@code CheckEvaluator}.
 */
@Component
public class DirectMeasurementResolver {

    private final ObjectProvider<DataSource> primaryDataSource;
    private final ObjectProvider<EndpointResolver> endpointResolver;
    private final ObjectProvider<NamedResourceResolver<DataSource>> namedDataSources;

    public DirectMeasurementResolver(ObjectProvider<DataSource> primaryDataSource) {
        this(primaryDataSource, null, null);
    }

    public DirectMeasurementResolver(ObjectProvider<DataSource> primaryDataSource,
                                      ObjectProvider<EndpointResolver> endpointResolver) {
        this(primaryDataSource, endpointResolver, null);
    }

    @Autowired
    public DirectMeasurementResolver(ObjectProvider<DataSource> primaryDataSource,
                                      ObjectProvider<EndpointResolver> endpointResolver,
                                      ObjectProvider<NamedResourceResolver<DataSource>> namedDataSources) {
        this.primaryDataSource = primaryDataSource;
        this.endpointResolver = endpointResolver;
        this.namedDataSources = namedDataSources;
    }

    /**
     * Resout la valeur d'une metrique directe.
     *
     * @param definition metrique du catalogue
     * @param context    contexte d'execution (pour les outputs de taches)
     * @param parameters parametres de l'etape d'assertion
     * @param window     fenetre de mesure
     * @return l'observation produite
     * @throws IllegalArgumentException si un parametre requis manque
     */
    public Observation resolve(MetricDefinition definition, ExecutionContext context,
                               Map<String, Object> parameters, TimeWindow window) {
        Objects.requireNonNull(definition, "definition required");
        String name = definition.id().toString();
        double value = switch (definition.id().namespace()) {
            case "database" -> resolveDatabase(name, parameters);
            case "filesystem" -> resolveFilesystem(name, definition, parameters);
            case "http" -> resolveHttpRequests(definition, context, parameters);
            default -> resolveTaskOutput(definition, context, parameters);
        };
        String source = sourceOf(parameters);
        return new Observation(definition.id(), value, definition.unit(), window,
                source, QueryFingerprint.internal(source, window),
                Map.of("metric", name));
    }

    // ------------------------------------------------------------------
    // Database
    // ------------------------------------------------------------------

    private double resolveDatabase(String name, Map<String, Object> parameters) {
        String query = requireText(parameters, "query");
        String datasourceName = optionalText(parameters, "datasource");
        JdbcTemplate jdbc = jdbcFor(datasourceName);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Double> future = executor.submit(() -> {
                if (name.endsWith(".updates")) {
                    Integer updated = jdbc.update(query);
                    return updated == null ? 0.0 : updated.doubleValue();
                }
                Double scalar = jdbc.query(query, rs -> {
                    if (!rs.next()) {
                        throw new IllegalStateException("Query returned no rows: " + query);
                    }
                    double result = rs.getDouble(1);
                    return rs.wasNull() ? null : result;
                });
                if (scalar == null) {
                    throw new IllegalStateException("Query returned NULL: " + query);
                }
                return scalar;
            });
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Database measurement interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Database measurement failed: " + cause.getMessage(), cause);
        }
    }

    private JdbcTemplate jdbcFor(String datasourceName) {
        if (datasourceName != null && !datasourceName.isBlank()
                && !"default".equals(datasourceName)) {
            NamedResourceResolver<DataSource> resolver =
                    namedDataSources == null ? null : namedDataSources.getIfAvailable();
            DataSource named = resolver == null ? null : resolver.resolve(datasourceName);
            if (named == null) {
                throw new IllegalArgumentException(
                        "Unknown datasource '" + datasourceName + "' (platform.datasources)");
            }
            return new JdbcTemplate(named);
        }
        DataSource dataSource = primaryDataSource.getIfAvailable();
        if (dataSource == null) {
            throw new IllegalStateException(
                    "No primary DataSource configured; database metrics are unavailable");
        }
        return new JdbcTemplate(dataSource);
    }

    // ------------------------------------------------------------------
    // Filesystem
    // ------------------------------------------------------------------

    private double resolveFilesystem(String name, MetricDefinition definition,
                                     Map<String, Object> parameters) {
        Path path = Path.of(requireText(parameters, "path"));
        return switch (name) {
            case "filesystem.exists" -> {
                boolean exists = Files.exists(path);
                boolean expected = Boolean.parseBoolean(
                        String.valueOf(parameters.getOrDefault("expect", true)));
                yield exists == expected ? 1.0 : 0.0;
            }
            case "filesystem.size" -> {
                try {
                    yield (double) Files.size(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Cannot read size of " + path, e);
                }
            }
            case "filesystem.checksum" -> {
                String expected = requireText(parameters, "checksum");
                String actual = "sha256:" + sha256(path);
                yield expected.equalsIgnoreCase(actual) ? 1.0 : 0.0;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported filesystem metric: " + definition.id());
        };
    }

    private static String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path);
                 DigestInputStream digestStream = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (digestStream.read(buffer) != -1) {
                    // digest updated by the stream
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path + " for checksum", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ------------------------------------------------------------------
    // HTTP mock (WireMock admin API or task output)
    // ------------------------------------------------------------------

    /**
     * Compte les appels recus par un mock.
     * <p>
     * Trois sources, dans cet ordre :
     * <ol>
     *   <li>{@code target} : cible HTTP nommee, resolue par le port
     *       {@link EndpointResolver} ;</li>
     *   <li>{@code mockUrl} : URL explicite de l'API d'administration WireMock
     *       ({@code /__admin/requests/count} ou {@code /__admin/requests/unmatched}) ;</li>
     *   <li>{@code refTaskId} : lit la sortie d'une tache de mock deja
     *       executee ({@code receivedCalls}, {@code unmatchedCalls}).</li>
     * </ol>
     */
    private double resolveHttpRequests(MetricDefinition definition, ExecutionContext context,
                                       Map<String, Object> parameters) {
        String target = optionalText(parameters, "target");
        if (target != null) {
            Endpoint endpoint = resolveEndpoint(target);
            return countFromWireMock(definition.id().toString(), endpoint.baseUrl(), parameters,
                    endpoint);
        }
        String mockUrl = optionalText(parameters, "mockUrl");
        if (mockUrl != null) {
            return countFromWireMock(definition.id().toString(), mockUrl.strip(), parameters, null);
        }
        return resolveTaskOutput(definition, context, parameters);
    }

    private Endpoint resolveEndpoint(String target) {
        EndpointResolver resolver = endpointResolver == null ? null : endpointResolver.getIfAvailable();
        if (resolver == null) {
            throw new IllegalArgumentException(
                    "No endpoint resolver configured for target '" + target + "'");
        }
        Endpoint endpoint = resolver.resolve(target);
        if (endpoint == null) {
            throw new IllegalArgumentException("Unknown http-target: '" + target + "'");
        }
        return endpoint;
    }

    private double countFromWireMock(String metric, String mockUrl, Map<String, Object> parameters,
                                     Endpoint endpoint) {
        String base = mockUrl.endsWith("/") ? mockUrl.substring(0, mockUrl.length() - 1) : mockUrl;
        try (var client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            if (metric.endsWith(".unmatched")) {
                var request = java.net.http.HttpRequest.newBuilder(
                                java.net.URI.create(base + "/__admin/requests/unmatched"))
                        .timeout(Duration.ofSeconds(10))
                        .GET().build();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                requireSuccess(response.statusCode(), metric);
                return countArrayEntries(response.body());
            }
            String endpointPath = optionalText(parameters, "endpoint");
            String method = optionalText(parameters, "method");
            StringBuilder body = new StringBuilder("{");
            if (method != null) {
                body.append("\"method\":\"").append(method.toUpperCase()).append("\",");
            }
            if (endpointPath != null) {
                body.append("\"url\":\"").append(endpointPath).append("\",");
            }
            if (body.length() > 1) {
                body.setLength(body.length() - 1);
            }
            body.append('}');
            String countPath = endpoint == null ? null
                    : endpoint.paths().get("count-requests");
            if (countPath == null) {
                countPath = "/__admin/requests/count";
            }
            var request = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(base + countPath))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            requireSuccess(response.statusCode(), metric);
            return readCount(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("WireMock measurement interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("WireMock measurement failed: " + e.getMessage(), e);
        }
    }

    private static void requireSuccess(int status, String metric) {
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(
                    "WireMock returned HTTP " + status + " for metric " + metric);
        }
    }

    /** Lit {@code {"count":N}} sans dependre d'un parseur JSON complet. */
    static double readCount(String json) {
        int index = json == null ? -1 : json.indexOf("\"count\"");
        if (index < 0) {
            throw new IllegalStateException("Unexpected WireMock response: " + json);
        }
        int colon = json.indexOf(':', index);
        int start = colon + 1;
        while (start < json.length() && !Character.isDigit(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        if (start >= end) {
            throw new IllegalStateException("Unexpected WireMock count value: " + json);
        }
        return Double.parseDouble(json.substring(start, end));
    }

    /** Compte les entrees d'un tableau JSON renvoye par WireMock. */
    static double countArrayEntries(String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        int open = json.indexOf('[');
        int close = json.lastIndexOf(']');
        if (open < 0 || close <= open) {
            return 0;
        }
        String array = json.substring(open + 1, close).strip();
        if (array.isEmpty()) {
            return 0;
        }
        double count = 1;
        for (int i = 0; i < array.length(); i++) {
            if (array.charAt(i) == '{' && i > 0) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Task outputs
    // ------------------------------------------------------------------

    private double resolveTaskOutput(MetricDefinition definition, ExecutionContext context,
                                     Map<String, Object> parameters) {
        String refTaskId = requireText(parameters, "refTaskId");
        String outputKey = optionalText(parameters, "output");
        if (outputKey == null) {
            outputKey = defaultOutputKey(definition.id().toString());
        }
        TaskResult result = context.getAll(refTaskId).values().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No task result for refTaskId='" + refTaskId + "'"));
        Object raw = result.outputs().get(outputKey);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text) {
            try {
                return Double.parseDouble(text.strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Output '" + outputKey + "' of task '" + refTaskId + "' is not numeric: " + raw);
            }
        }
        throw new IllegalArgumentException(
                "Output '" + outputKey + "' not found for task '" + refTaskId
                        + "'. Available: " + result.outputs().keySet());
    }

    private static String defaultOutputKey(String metric) {
        return switch (metric) {
            case "kafka.messages.consumed" -> "messagesConsumed";
            case "kafka.messages.produced" -> "messagesProduced";
            case "kafka.consumer.lag" -> "lag";
            case "http.requests.matched" -> "receivedCalls";
            case "http.requests.unmatched" -> "unmatchedCalls";
            case "injection.latency.p50" -> "p50";
            case "injection.latency.p95" -> "p95";
            case "injection.latency.p99" -> "p99";
            case "injection.throughput" -> "throughput";
            case "injection.error.rate" -> "errorRate";
            case "injection.requests.total" -> "totalRequests";
            default -> "value";
        };
    }

    private static String sourceOf(Map<String, Object> parameters) {
        String ref = optionalText(parameters, "refTaskId");
        return ref != null ? ref : "direct";
    }

    private static String requireText(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: '" + key + "'");
        }
        return value.toString().strip();
    }

    private static String optionalText(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString().strip();
    }

    /** Fenetre par defaut d'une mesure interne. */
    public static TimeWindow window(Instant start, Duration elapsed) {
        Duration safe = elapsed == null || elapsed.isNegative() ? Duration.ZERO : elapsed;
        return new TimeWindow(start, start.plus(safe), null);
    }
}
