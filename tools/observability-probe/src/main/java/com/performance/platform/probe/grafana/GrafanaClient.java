package com.performance.platform.probe.grafana;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.performance.platform.probe.Http;
import com.performance.platform.probe.Http;
import com.performance.platform.probe.ProbeConfig;
import com.performance.platform.probe.ProbeException;
import com.performance.platform.probe.model.ProbeFingerprint;
import com.performance.platform.probe.model.ProbePoint;
import com.performance.platform.probe.model.ProbeSeries;
import com.performance.platform.probe.model.ProbeWindow;
import com.performance.platform.probe.model.QueryMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecture Grafana : catalogue de dashboard et rejeu des requetes de panel via
 * {@code POST /api/ds/query}. Aucun rendu d'image n'est utilise.
 */
public final class GrafanaClient {

    private final ProbeConfig config;

    public GrafanaClient(ProbeConfig config) {
        this.config = config;
    }

    public Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + config.requireGrafanaToken());
        if (config.grafanaOrgId() != null) {
            headers.put("X-Grafana-Org-Id", config.grafanaOrgId());
        }
        return headers;
    }

    /** @return le dashboard complet (version + panels). */
    public JsonNode dashboard(String uid) {
        String url = config.requireGrafanaUrl() + "/api/dashboards/uid/" + uid;
        JsonNode response = Http.getJson(url, headers(), config.timeout());
        JsonNode dashboard = response.path("dashboard");
        if (dashboard.isMissingNode() || dashboard.isNull()) {
            throw ProbeException.insufficient("Dashboard '" + uid + "' not found in " + url);
        }
        return dashboard;
    }

    /** @return le panel demande, ou une erreur fonctionnelle. */
    public JsonNode panel(String uid, int panelId) {
        JsonNode panel = findPanel(dashboard(uid).path("panels"), panelId);
        if (panel == null) {
            throw ProbeException.insufficient("Panel " + panelId + " not found in dashboard " + uid);
        }
        return panel;
    }

    /**
     * Rejoue les requetes d'un panel.
     *
     * @param dashboardUid uid du dashboard
     * @param panelId      identifiant du panel
     * @param mappings     mapping refId -&gt; metrique (vide = tous les refIds)
     * @param window       fenetre demandee
     * @param stepMillis   resolution
     * @param variables    variables de template a substituer
     * @param maxDataPoints plafond de points
     * @return series lues
     */
    public List<ProbeSeries> query(String dashboardUid, int panelId, List<QueryMapping> mappings,
                                   ProbeWindow window, long stepMillis, Map<String, String> variables,
                                   int maxDataPoints) {
        JsonNode dashboard = dashboard(dashboardUid);
        JsonNode panel = findPanel(dashboard.path("panels"), panelId);
        if (panel == null) {
            throw ProbeException.insufficient("Panel " + panelId + " not found in dashboard " + dashboardUid);
        }
        JsonNode targets = panel.path("targets");
        if (!targets.isArray() || targets.isEmpty()) {
            throw ProbeException.insufficient(
                    "Panel " + panelId + " has no executable targets (no queries[])");
        }

        ObjectNode body = Http.MAPPER.createObjectNode();
        body.put("from", String.valueOf(window.fromEpochMillis()));
        body.put("to", String.valueOf(window.toEpochMillis()));
        ArrayNode queries = body.putArray("queries");
        Map<String, QueryMapping> byRef = new LinkedHashMap<>();
        for (QueryMapping mapping : mappings) {
            byRef.put(mapping.refId(), mapping);
        }

        for (int i = 0; i < targets.size(); i++) {
            JsonNode target = targets.get(i);
            String refId = target.path("refId").asText(String.valueOf((char) ('A' + i)));
            if (!byRef.isEmpty() && !byRef.containsKey(refId)) {
                continue;
            }
            ObjectNode query = target.deepCopy();
            query.put("refId", refId);
            if (!query.has("datasource") && panel.has("datasource")) {
                query.set("datasource", panel.get("datasource"));
            }
            if (!query.has("datasource")) {
                throw ProbeException.insufficient(
                        "Panel " + panelId + " target " + refId + " has no datasource uid");
            }
            query.put("format", "time_series");
            query.put("intervalMs", stepMillis);
            query.put("maxDataPoints", maxDataPoints);
            substituteVariables(query, variables);
            queries.add(query);
        }
        if (queries.isEmpty()) {
            throw ProbeException.insufficient(
                    "No panel target matches the --map refIds " + byRef.keySet());
        }

        String url = config.requireGrafanaUrl() + "/api/ds/query";
        JsonNode response = Http.postJson(url, body.toString(), headers(), config.timeout());
        String dashboardVersion = dashboard.path("version").asText("");
        return parse(response, byRef, window, dashboardUid, panelId, stepMillis, dashboardVersion);
    }

    // ------------------------------------------------------------------

    private List<ProbeSeries> parse(JsonNode response, Map<String, QueryMapping> byRef,
                                    ProbeWindow window, String dashboardUid, int panelId,
                                    long stepMillis, String dashboardVersion) {
        JsonNode results = response.path("results");
        if (results.isMissingNode() || results.isEmpty()) {
            throw ProbeException.remote("Grafana returned no results: " + response, null);
        }
        List<ProbeSeries> series = new ArrayList<>();
        var fields = results.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String refId = entry.getKey();
            JsonNode result = entry.getValue();
            if (result.has("error")) {
                throw ProbeException.insufficient("Query " + refId + " failed: "
                        + result.path("error").asText());
            }
            List<ProbePoint> points = new ArrayList<>();
            for (JsonNode frame : result.path("frames")) {
                points.addAll(extractPoints(frame));
            }
            QueryMapping mapping = byRef.get(refId);
            String metric = mapping == null ? "panel.series." + refId : mapping.metric();
            String unit = mapping == null ? "" : mapping.unit();
            series.add(new ProbeSeries(metric, unit, points));
        }
        return series;
    }

    /** Extrait (time, value) d'une frame Grafana. */
    static List<ProbePoint> extractPoints(JsonNode frame) {
        JsonNode schemaFields = frame.path("schema").path("fields");
        int timeIndex = -1;
        int valueIndex = -1;
        for (int i = 0; i < schemaFields.size(); i++) {
            String type = schemaFields.get(i).path("type").asText("");
            if (timeIndex < 0 && "time".equals(type)) {
                timeIndex = i;
            } else if (valueIndex < 0 && "number".equals(type)) {
                valueIndex = i;
            }
        }
        JsonNode values = frame.path("data").path("values");
        if (timeIndex < 0 || valueIndex < 0 || values.size() <= Math.max(timeIndex, valueIndex)) {
            return List.of();
        }
        JsonNode times = values.get(timeIndex);
        JsonNode numbers = values.get(valueIndex);
        List<ProbePoint> points = new ArrayList<>();
        int count = Math.min(times.size(), numbers.size());
        for (int i = 0; i < count; i++) {
            JsonNode value = numbers.get(i);
            if (value == null || value.isNull() || !value.isNumber()) {
                continue;
            }
            points.add(new ProbePoint(times.get(i).asLong(), value.asDouble()));
        }
        return points;
    }

    /** Empreinte des requetes reellement executees. */
    public ProbeFingerprint fingerprint(String dashboardUid, int panelId, String version,
                                        ProbeWindow window, long stepMillis, String resolvedQuery) {
        return new ProbeFingerprint("grafana", config.name(), "GRAFANA_DS_QUERY",
                dashboardUid + "/" + panelId, version, resolvedQuery,
                stepMillis + "ms", "", window.from().toString(), window.to().toString(),
                Instant.now());
    }

    // ------------------------------------------------------------------

    static JsonNode findPanel(JsonNode panels, int panelId) {
        if (panels == null || !panels.isArray()) {
            return null;
        }
        for (JsonNode panel : panels) {
            if (panel.path("id").asInt(-1) == panelId) {
                return panel;
            }
            JsonNode nested = findPanel(panel.path("panels"), panelId);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    static void substituteVariables(JsonNode node, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            var fieldNames = new ArrayList<String>();
            object.fieldNames().forEachRemaining(fieldNames::add);
            for (String field : fieldNames) {
                JsonNode child = object.get(field);
                if (child.isTextual()) {
                    object.put(field, replace(child.asText(), variables));
                } else {
                    substituteVariables(child, variables);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                substituteVariables(child, variables);
            }
        }
    }

    static String replace(String text, Map<String, String> variables) {
        String result = text;
        for (var entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue())
                    .replace("$" + entry.getKey(), entry.getValue());
        }
        return result;
    }
}
