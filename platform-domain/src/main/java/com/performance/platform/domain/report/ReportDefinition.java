package com.performance.platform.domain.report;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Declarative report context and evidence references, independent of collectors. */
public record ReportDefinition(
        Map<String, String> components, String environment, String objective, String testType,
        String loadDescription, List<String> assumptions, List<Criterion> criteria,
        List<Observation> observations, List<Graph> graphs, List<String> incidents,
        List<String> recommendations) {
    public ReportDefinition {
        components = components == null ? Map.of() : Map.copyOf(components);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
        observations = observations == null ? List.of() : List.copyOf(observations);
        graphs = graphs == null ? List.of() : List.copyOf(graphs);
        incidents = incidents == null ? List.of() : List.copyOf(incidents);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        components.forEach((name, version) -> {
            requireText(name, "component name");
            requireText(version, "component version");
        });
        if (observations.stream().map(Observation::name).distinct().count() != observations.size()) {
            throw new IllegalArgumentException("observation names must be unique");
        }
    }

    public record Criterion(String name, String metric, String taskId, String operator,
                            Double threshold, Boolean critical) {
        public Criterion {
            requireText(name, "criterion.name");
            requireText(metric, "criterion.metric");
            if (!Set.of("<", "<=", ">", ">=", "==", "!=").contains(operator == null ? "" : operator)) {
                throw new IllegalArgumentException("criterion.operator must be <, <=, >, >=, == or !=");
            }
            if (threshold == null || !Double.isFinite(threshold)) throw new IllegalArgumentException("criterion.threshold must be finite");
            critical = critical == null ? true : critical;
        }
    }

    /** A task output path or an externally measured, explicitly sourced value. */
    public record Observation(String name, String taskId, String output, Double value,
                              String unit, String source, boolean required) {
        public Observation {
            requireText(name, "observation.name");
            if (value != null) {
                if (!Double.isFinite(value)) throw new IllegalArgumentException("observation.value must be finite");
                requireText(source, "observation.source");
                if (taskId != null || output != null) {
                    throw new IllegalArgumentException("observation must use either a sourced value or a task output");
                }
            } else {
                requireText(taskId, "observation.taskId");
                requireText(output, "observation.output");
            }
        }
    }

    /** Link to a time-bounded dashboard/export, never fetched by the renderer. */
    public record Graph(String title, String url, String source, String imageDataUri) {
        public Graph(String title, String url, String source) {
            this(title, url, source, null);
        }

        public Graph {
            requireText(title, "graph.title");
            requireText(source, "graph.source");
            if ((url == null || url.isBlank()) && imageDataUri == null) {
                throw new IllegalArgumentException("graph requires a URL or exported PNG image");
            }
            if (url != null && !url.isBlank()) {
                var uri = URI.create(url);
                if (!Set.of("https", "http").contains(uri.getScheme() == null ? "" : uri.getScheme())
                        || uri.getHost() == null || uri.getUserInfo() != null) {
                    throw new IllegalArgumentException("graph.url must be an absolute HTTP(S) URL without credentials");
                }
            }
            if (imageDataUri != null) {
                String prefix = "data:image/png;base64,";
                if (!imageDataUri.startsWith(prefix) || imageDataUri.length() > 7_000_000) {
                    throw new IllegalArgumentException("graph.imageDataUri must be a PNG data URI (max 7 MB encoded)");
                }
                byte[] bytes = java.util.Base64.getDecoder().decode(imageDataUri.substring(prefix.length()));
                byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
                if (bytes.length < 24 || !java.util.Arrays.equals(signature, java.util.Arrays.copyOf(bytes, 8))) {
                    throw new IllegalArgumentException("graph.imageDataUri must contain a PNG image");
                }
                var dimensions = java.nio.ByteBuffer.wrap(bytes, 16, 8);
                int width = dimensions.getInt();
                int height = dimensions.getInt();
                if (width <= 0 || height <= 0 || (long) width * height > 16_000_000) {
                    throw new IllegalArgumentException("graph image dimensions exceed 16 megapixels");
                }
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
