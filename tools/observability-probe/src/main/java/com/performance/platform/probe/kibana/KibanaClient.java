package com.performance.platform.probe.kibana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.performance.platform.probe.Http;
import com.performance.platform.probe.ProbeConfig;
import com.performance.platform.probe.ProbeException;
import com.performance.platform.probe.model.ProbePoint;
import com.performance.platform.probe.model.ProbeSeries;
import com.performance.platform.probe.model.ProbeWindow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecture Kibana / Elasticsearch.
 * <p>
 * Deux canaux, configures par {@code mode} :
 * <ul>
 *   <li>{@code ES_DIRECT} : saved object puis {@code POST /<index>/_search}
 *       directement sur Elasticsearch (agregations completes) ;</li>
 *   <li>{@code CONSOLE_PROXY} : meme requete via
 *       {@code POST /api/console/proxy} (credentials Kibana).</li>
 * </ul>
 * Les routes internes {@code /internal/*} ne sont jamais utilisees.
 */
public final class KibanaClient {

    private final ProbeConfig config;

    public KibanaClient(ProbeConfig config) {
        this.config = config;
    }

    /** Descripteur de requete Elasticsearch. */
    public record EsQuery(String index, String timeField, String field, String aggregation,
                          Double percentile, String interval, String metric, String unit) {

        public EsQuery {
            timeField = timeField == null || timeField.isBlank() ? "@timestamp" : timeField;
            aggregation = aggregation == null || aggregation.isBlank() ? "avg" : aggregation;
            interval = interval == null || interval.isBlank() ? "1m" : interval;
            metric = metric == null || metric.isBlank() ? "panel.series.A" : metric;
            unit = unit == null ? "" : unit;
        }
    }

    private Map<String, String> kibanaHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (config.kibanaApiKey() != null && !config.kibanaApiKey().isBlank()) {
            headers.put("Authorization", "ApiKey " + config.kibanaApiKey());
        }
        headers.put("kbn-xsrf", "true");
        return headers;
    }

    private Map<String, String> elasticHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (config.elasticsearchApiKey() != null && !config.elasticsearchApiKey().isBlank()) {
            headers.put("Authorization", "ApiKey " + config.elasticsearchApiKey());
        }
        return headers;
    }

    /** @return l'objet sauvegarde (visualisation, dashboard, lens, search). */
    public JsonNode savedObject(String type, String id) {
        String url = config.requireKibanaUrl() + "/api/saved_objects/" + type + "/" + id;
        JsonNode response = Http.getJson(url, kibanaHeaders(), config.timeout());
        if (response.path("error").isMissingNode() == false) {
            throw ProbeException.insufficient("Kibana saved object " + type + "/" + id
                    + " unavailable: " + response.path("error").asText());
        }
        return response;
    }

    /**
     * Lit une serie sur la fenetre demandee.
     *
     * @param query  descripteur de requete
     * @param window fenetre
     * @param mode   {@code ES_DIRECT} ou {@code CONSOLE_PROXY} (null = config)
     * @return series lues
     */
    public List<ProbeSeries> query(EsQuery query, ProbeWindow window, String mode) {
        String effectiveMode = mode == null || mode.isBlank() ? config.mode() : mode.toUpperCase();
        ObjectNode body = buildSearchBody(query, window);
        JsonNode response;
        if (ProbeConfig.MODE_CONSOLE_PROXY.equals(effectiveMode)) {
            String url = config.requireKibanaUrl() + "/api/console/proxy?path="
                    + encodePath(query.index() + "/_search") + "&method=POST";
            response = Http.postJson(url, body.toString(), kibanaHeaders(), config.timeout());
        } else {
            String url = config.requireElasticsearchUrl() + "/" + query.index() + "/_search";
            try {
                response = Http.postJson(url, body.toString(), elasticHeaders(), config.timeout());
            } catch (ProbeException e) {
                if (config.fallback() == null) {
                    throw e;
                }
                String fallbackUrl = config.requireKibanaUrl() + "/api/console/proxy?path="
                        + encodePath(query.index() + "/_search") + "&method=POST";
                response = Http.postJson(fallbackUrl, body.toString(), kibanaHeaders(), config.timeout());
            }
        }
        return List.of(new ProbeSeries(query.metric(), query.unit(),
                extractPoints(response, query)));
    }

    /** Mode effectif retenu (utile pour l'empreinte). */
    public String effectiveMode(String requested) {
        return requested == null || requested.isBlank() ? config.mode() : requested.toUpperCase();
    }

    // ------------------------------------------------------------------

    static ObjectNode buildSearchBody(EsQuery query, ProbeWindow window) {
        ObjectNode body = Http.MAPPER.createObjectNode();
        body.put("size", 0);
        ObjectNode range = body.putObject("query").putObject("bool")
                .putArray("filter").addObject().putObject("range").putObject(query.timeField());
        range.put("gte", window.from().toString());
        range.put("lte", window.to().toString());

        ObjectNode series = body.putObject("aggs").putObject("series");
        ObjectNode histogram = series.putObject("date_histogram");
        histogram.put("field", query.timeField());
        histogram.put("fixed_interval", query.interval());
        histogram.put("min_doc_count", 1);

        ObjectNode agg = series.putObject("aggs").putObject("value");
        switch (query.aggregation().toLowerCase()) {
            case "count", "value_count" -> agg.putObject("value_count").put("field", query.field());
            case "percentiles" -> {
                ObjectNode percentiles = agg.putObject("percentiles");
                percentiles.put("field", query.field());
                percentiles.putArray("percents").add(query.percentile() == null ? 95.0 : query.percentile());
            }
            case "sum" -> agg.putObject("sum").put("field", query.field());
            case "max" -> agg.putObject("max").put("field", query.field());
            case "min" -> agg.putObject("min").put("field", query.field());
            default -> agg.putObject("avg").put("field", query.field());
        }
        return body;
    }

    static List<ProbePoint> extractPoints(JsonNode response, EsQuery query) {
        JsonNode buckets = response.path("aggregations").path("series").path("buckets");
        List<ProbePoint> points = new ArrayList<>();
        if (!buckets.isArray()) {
            return points;
        }
        for (JsonNode bucket : buckets) {
            long timestamp = bucket.path("key").asLong(0);
            JsonNode value = bucket.path("value");
            double number = valueNumber(value);
            if (Double.isNaN(number)) {
                continue;
            }
            points.add(new ProbePoint(timestamp, number));
        }
        return points;
    }

    private static double valueNumber(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Double.NaN;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.has("value") && value.get("value").isNumber()) {
            return value.get("value").asDouble();
        }
        // percentiles: {"values": {"95.0": 412.3}}
        JsonNode values = value.path("values");
        if (values.isObject() && !values.isEmpty()) {
            var iterator = values.fields();
            double max = -Double.MAX_VALUE;
            while (iterator.hasNext()) {
                JsonNode candidate = iterator.next().getValue();
                if (candidate.isNumber()) {
                    max = Math.max(max, candidate.asDouble());
                }
            }
            return max == -Double.MAX_VALUE ? Double.NaN : max;
        }
        return Double.NaN;
    }

    private static String encodePath(String path) {
        StringBuilder encoded = new StringBuilder();
        for (char character : path.toCharArray()) {
            if (Character.isLetterOrDigit(character) || "-._~/".indexOf(character) >= 0) {
                encoded.append(character);
            } else {
                encoded.append('%').append(String.format("%02X", (int) character));
            }
        }
        return encoded.toString();
    }
}
