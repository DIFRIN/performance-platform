package com.performance.platform.scenario.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.LoadPresets;
import com.performance.platform.domain.injection.WorkloadPlan;
import com.performance.platform.domain.injection.WorkloadPlanCodec;
import com.performance.platform.domain.scenario.AgentLossPolicy;
import com.performance.platform.domain.scenario.FanOut;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.ServiceScope;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.scenario.parser.dto.RetryPolicyYamlDto;
import com.performance.platform.scenario.parser.dto.ScenarioYamlDto;
import com.performance.platform.scenario.parser.dto.StepYamlDto;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation de ScenarioParser utilisant SnakeYAML pour le parsing brut
 * et Jackson pour le mapping vers les DTOs internes.
 * Les DTOs sont ensuite transformes en records domaine immuables.
 * 0 dependance Spring.
 */
public class YamlScenarioParser implements ScenarioParser {

    private static final Set<String> ROOT_KEYS = Set.of("scenario");
    private static final Set<String> SCENARIO_KEYS = Set.of(
            "id", "name", "version", "tags", "metadata",
            "taskAvailabilityTimeoutSeconds", "steps", "report");
    private static final Set<String> STEP_KEYS = Set.of(
            "id", "task", "phase", "description", "requiredContexts", "dependsOn",
            "ref", "during", "drain", "fanOut", "minAgents", "onAgentLoss",
            "parameters", "timeout", "retryPolicy");
    private static final Set<String> RETRY_KEYS = Set.of(
            "maxAttempts", "initialDelay", "multiplier", "maxDelay", "retryableExceptions");

    private final Yaml snakeYaml;
    private final ObjectMapper objectMapper;
    private final Duration defaultTimeout;

    /**
     * Construit un YamlScenarioParser avec une duree de timeout par defaut.
     *
     * @param defaultTimeout le timeout par defaut pour les steps qui n'en definissent pas
     */
    public YamlScenarioParser(Duration defaultTimeout) {
        this.snakeYaml = new Yaml();
        // Defense en profondeur : meme si la validation structurelle est
        // contournee, Jackson refuse une propriete inconnue.
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Construit un YamlScenarioParser avec le timeout par defaut de 5 minutes.
     */
    public YamlScenarioParser() {
        this(Duration.ofMinutes(5));
    }

    @Override
    public ScenarioDefinition parse(InputStream yamlContent) throws ScenarioParsingException {
        try {
            return parse(new String(yamlContent.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ScenarioParsingException(
                "Failed to read scenario YAML", List.of(e.getMessage())
            );
        }
    }

    @Override
    public ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException {
        if (yamlContent == null || yamlContent.isBlank()) {
            throw new ScenarioParsingException(
                "Failed to parse scenario YAML", List.of("YAML content is empty or null")
            );
        }
        validateStructure(yamlContent);
        var scenario = parseInternal(loadYaml(yamlContent));
        return new ScenarioDefinition(scenario.id(), scenario.name(), scenario.version(), scenario.tags(),
                scenario.metadata(), scenario.steps(), yamlContent, scenario.report());
    }

    @Override
    public ScenarioDefinition parseFile(Path scenarioFile) throws ScenarioParsingException {
        try (InputStream is = Files.newInputStream(scenarioFile)) {
            return parse(is);
        } catch (IOException e) {
            throw new ScenarioParsingException(
                "Failed to read scenario file: " + scenarioFile, List.of(e.getMessage())
            );
        }
    }

    // --- Internals ---

    private Map<String, Object> loadYaml(String content) {
        try {
            return snakeYaml.load(content);
        } catch (YAMLException e) {
            throw new ScenarioParsingException(
                "Failed to parse scenario YAML", List.of("YAML syntax error: " + e.getMessage()));
        }
    }

    /**
     * Valide la structure du document avant le mapping Jackson.
     * <p>
     * Refuse les proprietes inconnues et rapporte le numero de ligne :
     * une faute de frappe ({@code duraton:}) ne doit jamais produire un
     * scenario silencieusement different de celui ecrit. Les sections
     * libres ({@code parameters}, {@code metadata}) restent ouvertes.
     */
    private void validateStructure(String content) {
        List<String> errors = new ArrayList<>();
        Node root;
        try {
            root = snakeYaml.compose(new StringReader(content));
        } catch (YAMLException e) {
            errors.add("YAML syntax error: " + e.getMessage());
            throw new ScenarioParsingException(
                "Failed to parse scenario YAML", errors);
        }
        if (root == null) {
            errors.add("YAML content is empty or null");
            throw new ScenarioParsingException("Failed to parse scenario YAML", errors);
        }

        MappingNode rootMapping = asMapping(root, "root", errors);
        if (rootMapping != null) {
            validateKeys(rootMapping, "root", ROOT_KEYS, errors);
            for (NodeTuple tuple : rootMapping.getValue()) {
                if ("scenario".equals(keyOf(tuple))) {
                    MappingNode scenario = asMapping(tuple.getValueNode(), "scenario", errors);
                    if (scenario != null) {
                        validateScenario(scenario, errors);
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ScenarioParsingException(
                "Failed to parse scenario YAML: " + errors.size() + " error(s)", errors);
        }
    }

    private void validateScenario(MappingNode scenario, List<String> errors) {
        validateKeys(scenario, "scenario", SCENARIO_KEYS, errors);
        for (NodeTuple tuple : scenario.getValue()) {
            if ("steps".equals(keyOf(tuple))) {
                validateSteps(tuple.getValueNode(), errors);
            }
        }
    }

    private void validateSteps(Node stepsNode, List<String> errors) {
        if (!(stepsNode instanceof SequenceNode sequence)) {
            errors.add(location(stepsNode) + "'steps' must be a sequence in scenario");
            return;
        }
        int index = 0;
        for (Node item : sequence.getValue()) {
            String path = "scenario.steps[" + index + "]";
            MappingNode step = asMapping(item, path, errors);
            if (step != null) {
                validateKeys(step, path, STEP_KEYS, errors);
                for (NodeTuple tuple : step.getValue()) {
                    if ("retryPolicy".equals(keyOf(tuple))) {
                        MappingNode retry = asMapping(tuple.getValueNode(), path + ".retryPolicy", errors);
                        if (retry != null) {
                            validateKeys(retry, path + ".retryPolicy", RETRY_KEYS, errors);
                        }
                    }
                }
            }
            index++;
        }
    }

    private void validateKeys(MappingNode node, String path, Set<String> allowed,
                              List<String> errors) {
        for (NodeTuple tuple : node.getValue()) {
            if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) {
                continue;
            }
            String key = keyNode.getValue();
            if (!allowed.contains(key)) {
                errors.add(location(keyNode) + "unknown property '" + key + "' in " + path);
            }
        }
    }

    private MappingNode asMapping(Node node, String path, List<String> errors) {
        if (node instanceof MappingNode mapping) {
            return mapping;
        }
        errors.add(location(node) + "'" + path + "' must be a mapping");
        return null;
    }

    private static String keyOf(NodeTuple tuple) {
        return tuple.getKeyNode() instanceof ScalarNode scalar ? scalar.getValue() : "";
    }

    private static String location(Node node) {
        if (node == null || node.getStartMark() == null) {
            return "";
        }
        return "line " + (node.getStartMark().getLine() + 1) + ": ";
    }

    private ScenarioDefinition parseInternal(Map<String, Object> rawYaml) throws ScenarioParsingException {
        List<String> errors = new ArrayList<>();

        if (rawYaml == null || rawYaml.isEmpty()) {
            errors.add("YAML content is empty or null");
            throw new ScenarioParsingException("Failed to parse scenario YAML", errors);
        }

        // Convertir la section scenario en DTO
        ScenarioYamlDto dto;
        try {
            Object scenarioNode = rawYaml.get("scenario");
            if (scenarioNode == null) {
                errors.add("Missing required 'scenario' section");
                throw new ScenarioParsingException("Failed to parse scenario YAML", errors);
            }
            dto = objectMapper.convertValue(scenarioNode, ScenarioYamlDto.class);
        } catch (IllegalArgumentException e) {
            errors.add("YAML structure error: " + e.getMessage());
            throw new ScenarioParsingException("Failed to parse scenario YAML", errors);
        }

        // Mapper vers le domaine
        ScenarioId scenarioId = parseScenarioId(dto, errors);
        List<StepDefinition> steps = parseSteps(dto, errors);

        if (!errors.isEmpty()) {
            throw new ScenarioParsingException("Failed to parse scenario YAML: " + errors.size() + " error(s)", errors);
        }

        return new ScenarioDefinition(
            scenarioId,
            dto.name(),
            dto.version(),
            dto.tags() != null ? dto.tags() : List.of(),
            dto.metadata() != null ? dto.metadata() : Map.of(),
            steps,
            null,
            dto.report()
        );
    }

    private ScenarioId parseScenarioId(ScenarioYamlDto dto, List<String> errors) {
        if (dto.id() == null || dto.id().isBlank()) {
            errors.add("scenario.id is required");
            return null;
        }
        try {
            return ScenarioId.of(dto.id());
        } catch (Exception e) {
            errors.add("scenario.id: " + e.getMessage());
            return null;
        }
    }

    private List<StepDefinition> parseSteps(ScenarioYamlDto dto, List<String> errors) {
        if (dto.steps() == null || dto.steps().isEmpty()) {
            return List.of();
        }

        List<StepDefinition> steps = new ArrayList<>();
        for (int i = 0; i < dto.steps().size(); i++) {
            StepYamlDto stepDto = dto.steps().get(i);
            String prefix = "steps[" + i + "]";
            StepDefinition step = parseStep(stepDto, prefix, errors);
            if (step != null) {
                steps.add(step);
            }
        }
        return steps;
    }

    private StepDefinition parseStep(StepYamlDto dto, String prefix, List<String> errors) {
        if (dto == null) {
            errors.add(prefix + ": step is null");
            return null;
        }

        // id
        TaskId taskId = null;
        if (dto.id() == null || dto.id().isBlank()) {
            errors.add(prefix + ".id is required");
        } else {
            try {
                taskId = TaskId.of(dto.id());
            } catch (Exception e) {
                errors.add(prefix + ".id: " + e.getMessage());
            }
        }

        // taskName
        String taskName = dto.task();
        if (taskName == null || taskName.isBlank()) {
            errors.add(prefix + ".task is required (example task: " + (dto.id() != null ? dto.id() : "?") + ")");
        }

        // phase / during : une action step declare phase, une service step declare during
        Phase phase = null;
        ServiceScope during = parseDuring(dto.during(), prefix, errors);

        if (during == null) {
            if (dto.phase() == null || dto.phase().isBlank()) {
                errors.add(prefix + ".phase is required. Expected PREPARATION, INJECTION, or ASSERTION");
            } else {
                try {
                    phase = Phase.valueOf(dto.phase().toUpperCase());
                } catch (IllegalArgumentException e) {
                    errors.add(prefix + ".phase: invalid value '" + dto.phase()
                        + "'. Expected PREPARATION, INJECTION, or ASSERTION");
                }
            }
        } else if (dto.phase() != null && !dto.phase().isBlank()) {
            errors.add(prefix + ".phase: a service step (during: " + dto.during()
                + ") must not declare a phase");
        }

        // parameters
        Map<String, Object> parameters = dto.parameters() != null
            ? new HashMap<>(dto.parameters())
            : new HashMap<>();

        normalizeWorkload(parameters, prefix, errors);

        // description (SLI/SLO textuel) — conservee dans les parametres sous
        // la cle "description" pour ne pas modifier StepDefinition (ADR-049).
        if (dto.description() != null && !dto.description().isEmpty()) {
            parameters.put("description", new HashMap<>(dto.description()));
        }

        // dependsOn
        List<TaskId> dependsOn = parseDependsOn(dto, prefix, errors);

        // ref (ADR-053, premiere classe)
        TaskId ref = parseRef(dto.ref(), prefix, errors);

        // drain : uniquement pour une service step (ADR-054)
        Duration drain = Duration.ZERO;
        if (dto.drain() != null && !dto.drain().isBlank()) {
            if (during == null) {
                errors.add(prefix + ".drain is only valid on a service step (during: ...)");
            } else {
                try {
                    drain = DurationParser.parse(dto.drain());
                } catch (IllegalArgumentException e) {
                    errors.add(prefix + ".drain: " + e.getMessage());
                }
            }
        }

        // fanOut (ADR-055)
        FanOut fanOut = parseFanOut(dto.fanOut(), prefix, errors);

        // requiredContexts (override explicite ; defaut = dependsOn + ref)
        List<String> requiredContexts = dto.requiredContexts() != null
            ? List.copyOf(dto.requiredContexts())
            : List.of();

        // timeout
        Duration timeout = parseTimeout(dto.timeout(), prefix, errors);

        // retryPolicy
        RetryPolicy retryPolicy = parseRetryPolicy(dto.retryPolicy(), prefix, errors);

        // minAgents / onAgentLoss (ADR-067)
        String label = prefix + (dto.id() != null ? " (id=" + dto.id() + ")" : "");
        int minAgents = 1;
        if (dto.minAgents() != null) {
            if (dto.minAgents() < 1) {
                errors.add(label + ".minAgents: must be >= 1 but was " + dto.minAgents());
            } else {
                minAgents = dto.minAgents();
            }
        }
        AgentLossPolicy onAgentLoss = AgentLossPolicy.ABORT;
        if (dto.onAgentLoss() != null && !dto.onAgentLoss().isBlank()) {
            try {
                onAgentLoss = AgentLossPolicy.valueOf(dto.onAgentLoss().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(label + ".onAgentLoss: invalid value '" + dto.onAgentLoss()
                    + "'. Expected abort or continue");
            }
        }

        // Si l'id est manquant on ne peut pas construire
        if (taskId == null) {
            return null;
        }

        try {
            return new StepDefinition(
                taskId,
                taskName != null ? taskName : "",
                phase,
                parameters,
                dependsOn,
                ref,
                during,
                drain,
                fanOut,
                requiredContexts,
                timeout,
                retryPolicy,
                minAgents,
                onAgentLoss
            );
        } catch (Exception e) {
            errors.add(prefix + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Normalise l'injection (ADR-064) : {@code loadModel} (preset) est developpe en
     * {@code workload} canonique ; {@code workload} est valide puis re-encode.
     */
    @SuppressWarnings("unchecked")
    private void normalizeWorkload(Map<String, Object> parameters, String prefix, List<String> errors) {
        boolean hasWorkload = parameters.containsKey(WorkloadPlanCodec.PARAM_KEY);
        boolean hasLoadModel = parameters.containsKey("loadModel");
        if (!hasWorkload && !hasLoadModel) {
            return;
        }
        String stepId = prefix;
        if (hasWorkload && hasLoadModel) {
            errors.add(stepId + ".parameters: 'workload' and 'loadModel' are mutually exclusive");
            return;
        }
        try {
            WorkloadPlan plan;
            if (hasWorkload) {
                if (!(parameters.get("workload") instanceof Map<?, ?> m)) {
                    errors.add(stepId + ".parameters.workload must be a mapping");
                    return;
                }
                plan = WorkloadPlanCodec.fromMap((Map<String, Object>) m);
            } else {
                if (!(parameters.get("loadModel") instanceof Map<?, ?> m)) {
                    errors.add(stepId + ".parameters.loadModel must be a mapping");
                    return;
                }
                Object type = m.get("type");
                Object params = m.get("parameters");
                if (params != null && !(params instanceof Map<?, ?>)) {
                    errors.add(stepId + ".parameters.loadModel.parameters must be a mapping");
                    return;
                }
                plan = LoadPresets.expand(type == null ? null : type.toString(),
                        (Map<String, Object>) params);
            }
            parameters.remove("loadModel");
            parameters.put(WorkloadPlanCodec.PARAM_KEY, WorkloadPlanCodec.toMap(plan));
        } catch (IllegalArgumentException e) {
            errors.add(stepId + ".parameters." + (hasWorkload ? "workload" : "loadModel") + ": " + e.getMessage());
        }
    }

    private ServiceScope parseDuring(String during, String prefix, List<String> errors) {
        if (during == null || during.isBlank()) {
            return null;
        }
        try {
            return ServiceScope.valueOf(during.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(prefix + ".during: invalid value '" + during
                + "'. Expected INJECTION or SCENARIO");
            return null;
        }
    }

    private FanOut parseFanOut(String fanOut, String prefix, List<String> errors) {
        if (fanOut == null || fanOut.isBlank()) {
            return FanOut.ONE;
        }
        try {
            return FanOut.valueOf(fanOut.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(prefix + ".fanOut: invalid value '" + fanOut + "'. Expected one or all");
            return FanOut.ONE;
        }
    }

    private TaskId parseRef(String ref, String prefix, List<String> errors) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        try {
            return TaskId.of(ref);
        } catch (Exception e) {
            errors.add(prefix + ".ref: " + e.getMessage());
            return null;
        }
    }

    private List<TaskId> parseDependsOn(StepYamlDto dto, String prefix, List<String> errors) {
        if (dto.dependsOn() == null || dto.dependsOn().isEmpty()) {
            return List.of();
        }
        List<TaskId> result = new ArrayList<>();
        for (int i = 0; i < dto.dependsOn().size(); i++) {
            String depId = dto.dependsOn().get(i);
            if (depId == null || depId.isBlank()) {
                errors.add(prefix + ".dependsOn[" + i + "] is blank");
                continue;
            }
            try {
                result.add(TaskId.of(depId));
            } catch (Exception e) {
                errors.add(prefix + ".dependsOn[" + i + "]: " + e.getMessage());
            }
        }
        return result;
    }

    private Duration parseTimeout(String timeoutStr, String prefix, List<String> errors) {
        if (timeoutStr == null || timeoutStr.isBlank()) {
            return defaultTimeout;
        }
        try {
            return DurationParser.parse(timeoutStr);
        } catch (IllegalArgumentException e) {
            errors.add(prefix + ".timeout: " + e.getMessage());
            return defaultTimeout;
        }
    }

    private RetryPolicy parseRetryPolicy(RetryPolicyYamlDto dto, String prefix, List<String> errors) {
        if (dto == null) {
            return null;
        }

        int maxAttempts = dto.maxAttempts() != null ? dto.maxAttempts() : 3;
        double multiplier = dto.multiplier() != null ? dto.multiplier() : 2.0;

        Duration initialDelay;
        try {
            initialDelay = DurationParser.parseOrDefault(dto.initialDelay(), Duration.ofSeconds(1));
        } catch (IllegalArgumentException e) {
            errors.add(prefix + ".retryPolicy.initialDelay: " + e.getMessage());
            initialDelay = Duration.ofSeconds(1);
        }

        Duration maxDelay;
        try {
            maxDelay = DurationParser.parseOrDefault(dto.maxDelay(), Duration.ofSeconds(30));
        } catch (IllegalArgumentException e) {
            errors.add(prefix + ".retryPolicy.maxDelay: " + e.getMessage());
            maxDelay = Duration.ofSeconds(30);
        }

        try {
            return new RetryPolicy(maxAttempts, initialDelay, multiplier, maxDelay, null);
        } catch (IllegalArgumentException e) {
            errors.add(prefix + ".retryPolicy: " + e.getMessage());
            return null;
        }
    }
}
