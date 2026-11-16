package com.performance.platform.probe;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration d'une source logique.
 * <p>
 * Priorite : options CLI &gt; variables d'environnement &gt; {@code probe.yaml}.
 * Aucun secret n'est accepte en argument positionnel.
 */
public record ProbeConfig(String name, String provider, String mode,
                          String grafanaUrl, String grafanaToken, String grafanaOrgId,
                          String kibanaUrl, String kibanaApiKey,
                          String elasticsearchUrl, String elasticsearchApiKey,
                          String fallback, Duration timeout) {

    public static final String PROVIDER_GRAFANA = "GRAFANA";
    public static final String PROVIDER_KIBANA = "KIBANA";
    public static final String MODE_ES_DIRECT = "ES_DIRECT";
    public static final String MODE_CONSOLE_PROXY = "CONSOLE_PROXY";

    /**
     * Charge la configuration d'une source.
     *
     * @param sourceName nom logique ({@code grafana-prod})
     * @param configFile fichier optionnel {@code probe.yaml}
     */
    public static ProbeConfig load(String sourceName, Path configFile) {
        Map<String, Object> yaml = readYaml(configFile, sourceName);
        String provider = first(text(yaml, "provider"), env("PROBE_PROVIDER"));
        if (provider == null) {
            provider = env("GRAFANA_URL") != null ? PROVIDER_GRAFANA
                    : env("KIBANA_URL") != null ? PROVIDER_KIBANA : null;
        }
        if (provider == null) {
            throw ProbeException.config(
                    "No provider configured for source '" + sourceName
                            + "'. Set GRAFANA_URL or KIBANA_URL, or use probe.yaml");
        }
        provider = provider.toUpperCase();
        String mode = first(text(yaml, "mode"), env("PROBE_KIBANA_MODE"), MODE_ES_DIRECT);
        return new ProbeConfig(
                sourceName,
                provider,
                mode.toUpperCase(),
                first(text(yaml, "url"), env("GRAFANA_URL")),
                first(text(yaml, "token"), env("GRAFANA_TOKEN")),
                first(text(yaml, "orgId"), env("GRAFANA_ORG_ID"), "1"),
                first(text(yaml, "kibanaUrl"), env("KIBANA_URL")),
                first(text(yaml, "kibanaApiKey"), env("KIBANA_API_KEY")),
                first(text(yaml, "elasticsearchUrl"), env("ES_URL")),
                first(text(yaml, "elasticsearchApiKey"), env("ES_API_KEY")),
                first(text(yaml, "fallback"), env("PROBE_KIBANA_FALLBACK")),
                TimeParser.parseDuration(first(text(yaml, "timeout"), env("PROBE_TIMEOUT")),
                        Duration.ofSeconds(30)));
    }

    public boolean isGrafana() {
        return PROVIDER_GRAFANA.equals(provider);
    }

    public boolean isKibana() {
        return PROVIDER_KIBANA.equals(provider);
    }

    public String requireGrafanaUrl() {
        if (grafanaUrl == null || grafanaUrl.isBlank()) {
            throw ProbeException.config("GRAFANA_URL is required for source '" + name + "'");
        }
        return stripTrailingSlash(grafanaUrl);
    }

    public String requireGrafanaToken() {
        if (grafanaToken == null || grafanaToken.isBlank()) {
            throw ProbeException.config("GRAFANA_TOKEN is required for source '" + name + "'");
        }
        return grafanaToken;
    }

    public String requireKibanaUrl() {
        if (kibanaUrl == null || kibanaUrl.isBlank()) {
            throw ProbeException.config("KIBANA_URL is required for source '" + name + "'");
        }
        return stripTrailingSlash(kibanaUrl);
    }

    public String requireElasticsearchUrl() {
        if (elasticsearchUrl == null || elasticsearchUrl.isBlank()) {
            throw ProbeException.config("ES_URL is required for ES_DIRECT mode");
        }
        return stripTrailingSlash(elasticsearchUrl);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readYaml(Path configFile, String sourceName) {
        Path path = configFile;
        if (path == null) {
            String envPath = env("PROBE_CONFIG");
            path = envPath == null ? null : Path.of(envPath);
        }
        if (path == null || !Files.isRegularFile(path)) {
            return Map.of();
        }
        try (InputStream in = Files.newInputStream(path)) {
            Object root = new Yaml().load(in);
            if (!(root instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Object sources = map.get("sources");
            if (sources instanceof Map<?, ?> sourceMap) {
                Object entry = sourceMap.get(sourceName);
                if (entry instanceof Map<?, ?> source) {
                    return (Map<String, Object>) source;
                }
            }
            return Map.of();
        } catch (IOException e) {
            throw ProbeException.config("Cannot read " + path + ": " + e.getMessage());
        }
    }

    private static String text(Map<String, Object> yaml, String key) {
        Object value = yaml.get(key);
        return value == null ? null : value.toString();
    }

    private static String env(String key) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    /** Vue lisible (sans secrets), utilisee par {@code catalog --explain}. */
    public Map<String, String> describe() {
        Map<String, String> view = new LinkedHashMap<>();
        view.put("name", name);
        view.put("provider", provider);
        view.put("mode", mode);
        view.put("grafanaUrl", grafanaUrl == null ? "" : grafanaUrl);
        view.put("kibanaUrl", kibanaUrl == null ? "" : kibanaUrl);
        view.put("elasticsearchUrl", elasticsearchUrl == null ? "" : elasticsearchUrl);
        view.put("fallback", fallback == null ? "" : fallback);
        view.put("timeout", timeout.toString());
        return view;
    }
}
