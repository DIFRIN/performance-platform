package com.performance.platform.injection.gatling.worker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Run directory d'un worker : {@code spec.json}, {@code load-model.json} (entrees),
 * {@code results/} (Gatling) et {@code result.json} (sortie).
 * Aucun secret n'est ecrit : {@link WorkerRunSpec#environment()} est ignore.
 */
public final class RunDirectory {

    public static final String SPEC_FILE = "spec.json";
    public static final String LOAD_MODEL_FILE = "load-model.json";
    public static final String RESULT_FILE = "result.json";
    public static final String RESULTS_DIR = "results";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final Path root;

    public RunDirectory(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public Path resultsDirectory() {
        return root.resolve(RESULTS_DIR);
    }

    public Path resultFile() {
        return root.resolve(RESULT_FILE);
    }

    public void writeInputs(WorkerRunSpec spec, LoadModel loadModel) throws IOException {
        Files.createDirectories(root);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("simulationId", spec.simulationId());
        s.put("simulationClass", spec.simulationClass());
        s.put("systemProperties", spec.systemProperties());
        s.put("timeout", spec.timeout().toString());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(root.resolve(SPEC_FILE).toFile(), s);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", loadModel.type().name());
        m.put("parameters", loadModel.parameters());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(root.resolve(LOAD_MODEL_FILE).toFile(), m);
    }

    /** Lit le spec ; {@code environment} est toujours vide. */
    @SuppressWarnings("unchecked")
    public WorkerRunSpec readSpec() throws IOException {
        Map<String, Object> s = MAPPER.readValue(root.resolve(SPEC_FILE).toFile(), MAP_TYPE);
        try {
            Map<String, String> props = new LinkedHashMap<>();
            Object raw = s.get("systemProperties");
            if (raw instanceof Map<?, ?> rm) {
                rm.forEach((k, v) -> props.put(String.valueOf(k), String.valueOf(v)));
            }
            return new WorkerRunSpec(root, (String) s.get("simulationId"), (String) s.get("simulationClass"),
                    props, Map.of(), Duration.parse(String.valueOf(s.getOrDefault("timeout", "PT0S"))));
        } catch (RuntimeException e) {
            throw new IOException("Invalid " + SPEC_FILE + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public LoadModel readLoadModel() throws IOException {
        Map<String, Object> m = MAPPER.readValue(root.resolve(LOAD_MODEL_FILE).toFile(), MAP_TYPE);
        try {
            Object params = m.get("parameters");
            return new LoadModel(LoadModelType.valueOf((String) m.get("type")),
                    params instanceof Map<?, ?> ? (Map<String, Object>) params : Map.of());
        } catch (RuntimeException e) {
            throw new IOException("Invalid " + LOAD_MODEL_FILE + ": " + e.getMessage(), e);
        }
    }

    public void writeResult(WorkerOutcome outcome) throws IOException {
        Files.createDirectories(root);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("exitCode", outcome.exitCode());
        r.put("completed", outcome.completed());
        r.put("stoppedByPlatform", outcome.stoppedByPlatform());
        r.put("resultsDirectory", outcome.resultsDirectory() == null ? null : outcome.resultsDirectory().toString());
        r.put("logTail", outcome.logTail());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(resultFile().toFile(), r);
    }

    public WorkerOutcome readResult() throws IOException {
        Map<String, Object> r = MAPPER.readValue(resultFile().toFile(), MAP_TYPE);
        Object rd = r.get("resultsDirectory");
        return new WorkerOutcome(((Number) r.get("exitCode")).intValue(),
                Boolean.TRUE.equals(r.get("completed")), Boolean.TRUE.equals(r.get("stoppedByPlatform")),
                rd == null ? null : Path.of(rd.toString()), resultFile(),
                r.get("logTail") == null ? "" : r.get("logTail").toString());
    }
}
