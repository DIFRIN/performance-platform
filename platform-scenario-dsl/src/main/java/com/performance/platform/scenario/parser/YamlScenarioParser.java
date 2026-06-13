package com.performance.platform.scenario.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.execution.RetryPolicy;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.injection.LoadModel;
import com.performance.platform.domain.injection.LoadModelType;
import com.performance.platform.domain.scenario.ExecutionMode;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;
import com.performance.platform.scenario.parser.dto.LoadModelYamlDto;
import com.performance.platform.scenario.parser.dto.RetryPolicyYamlDto;
import com.performance.platform.scenario.parser.dto.ScenarioYamlDto;
import com.performance.platform.scenario.parser.dto.ScenarioYamlRoot;
import com.performance.platform.scenario.parser.dto.StepYamlDto;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation de ScenarioParser utilisant SnakeYAML pour le parsing brut
 * et Jackson pour le mapping vers les DTOs internes.
 * Les DTOs sont ensuite transformes en records domaine immuables.
 * 0 dependance Spring.
 */
public class YamlScenarioParser implements ScenarioParser {

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
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
        return parseInternal(loadYaml(yamlContent));
    }

    @Override
    public ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException {
        if (yamlContent == null || yamlContent.isBlank()) {
            throw new ScenarioParsingException(
                "Failed to parse scenario YAML", List.of("YAML content is empty or null")
            );
        }
        return parseInternal(loadYaml(yamlContent));
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

    private Map<String, Object> loadYaml(InputStream inputStream) {
        return snakeYaml.load(inputStream);
    }

    private Map<String, Object> loadYaml(String content) {
        return snakeYaml.load(content);
    }

    private ScenarioDefinition parseInternal(Map<String, Object> rawYaml) throws ScenarioParsingException {
        List<String> errors = new ArrayList<>();

        if (rawYaml == null || rawYaml.isEmpty()) {
            errors.add("YAML content is empty or null");
            throw new ScenarioParsingException("Failed to parse scenario YAML", errors);
        }

        // Convertir le map brut en DTO
        ScenarioYamlRoot root;
        try {
            root = objectMapper.convertValue(rawYaml, ScenarioYamlRoot.class);
        } catch (IllegalArgumentException e) {
            errors.add("YAML structure error: " + e.getMessage());
            throw new ScenarioParsingException("Failed to parse scenario YAML", errors);
        }

        if (root.scenario() == null) {
            errors.add("Missing required 'scenario' section");
            throw new ScenarioParsingException("Failed to parse scenario YAML", errors);
        }

        ScenarioYamlDto dto = root.scenario();

        // Mapper vers le domaine
        ScenarioId scenarioId = parseScenarioId(dto, errors);
        ExecutionMode executionMode = parseExecutionMode(dto, errors);
        List<StepDefinition> steps = parseSteps(dto, errors);
        Map<String, LoadModel> loadModels = parseLoadModels(root.loadModels(), errors);

        if (!errors.isEmpty()) {
            throw new ScenarioParsingException("Failed to parse scenario YAML: " + errors.size() + " error(s)", errors);
        }

        return new ScenarioDefinition(
            scenarioId,
            dto.name(),
            dto.version(),
            dto.tags() != null ? dto.tags() : List.of(),
            dto.metadata() != null ? dto.metadata() : Map.of(),
            executionMode,
            steps,
            loadModels
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

    private ExecutionMode parseExecutionMode(ScenarioYamlDto dto, List<String> errors) {
        if (dto.execution() == null || dto.execution().mode() == null || dto.execution().mode().isBlank()) {
            return ExecutionMode.LOCAL;
        }
        try {
            return ExecutionMode.valueOf(dto.execution().mode().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add("execution.mode: invalid value '" + dto.execution().mode()
                + "'. Expected LOCAL or DISTRIBUTED");
            return ExecutionMode.LOCAL;
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

        // phase
        Phase phase = null;
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

        // parameters
        Map<String, Object> parameters = dto.parameters() != null
            ? new HashMap<>(dto.parameters())
            : Map.of();

        // dependsOn
        List<TaskId> dependsOn = parseDependsOn(dto, prefix, errors);

        // requiredContexts
        List<String> requiredContexts = dto.requiredContexts() != null
            ? List.copyOf(dto.requiredContexts())
            : List.of();

        // timeout
        Duration timeout = parseTimeout(dto.timeout(), prefix, errors);

        // retryPolicy
        RetryPolicy retryPolicy = parseRetryPolicy(dto.retryPolicy(), prefix, errors);

        // Si l'id est manquant on ne peut pas construire
        if (taskId == null) {
            return null;
        }

        try {
            return new StepDefinition(
                taskId,
                taskName != null ? taskName : "",
                phase != null ? phase : Phase.PREPARATION,
                parameters,
                dependsOn,
                requiredContexts,
                timeout,
                retryPolicy
            );
        } catch (Exception e) {
            errors.add(prefix + ": " + e.getMessage());
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

    private Map<String, LoadModel> parseLoadModels(Map<String, LoadModelYamlDto> loadModelsDto, List<String> errors) {
        if (loadModelsDto == null || loadModelsDto.isEmpty()) {
            return Map.of();
        }

        Map<String, LoadModel> result = new LinkedHashMap<>();
        for (Map.Entry<String, LoadModelYamlDto> entry : loadModelsDto.entrySet()) {
            String name = entry.getKey();
            LoadModelYamlDto dto = entry.getValue();

            if (dto == null) {
                errors.add("loadModels." + name + ": load model is null");
                continue;
            }

            if (dto.getType() == null || dto.getType().isBlank()) {
                errors.add("loadModels." + name + ".type is required");
                continue;
            }

            LoadModelType type;
            try {
                type = LoadModelType.valueOf(dto.getType().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add("loadModels." + name + ".type: invalid value '" + dto.getType()
                    + "'. Expected one of: RAMP, CONSTANT, SPIKE, STAIR, SOAK, BURST, RAMP_UP_DOWN, CUSTOM");
                continue;
            }

            Map<String, Object> parameters = new LinkedHashMap<>(dto.getParameters());
            result.put(name, new LoadModel(type, parameters));
        }

        return result;
    }
}
