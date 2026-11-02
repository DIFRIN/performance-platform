package com.performance.platform.app.catalog;

import com.performance.platform.application.ports.in.ScenarioParsingUseCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Catalogue de scenarios en lecture seule, alimente par un repertoire configure
 * ({@code platform.scenarios.directory} / {@code SCENARIOS_DIR}).
 * <p>
 * Le repertoire est re-scanne a chaque appel : ajouter ou remplacer un fichier
 * YAML ne necessite pas de redemarrage. Les fichiers invalides sont exposes
 * avec leur message d'erreur plutot que d'etre ignores, afin que l'IHM puisse
 * les signaler.
 * <p>
 * Seuls les fichiers du premier niveau sont charges (pas de scan recursif).
 */
@Service
public class ScenarioCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioCatalogService.class);

    private final ScenarioCatalogProperties properties;
    private final ScenarioParsingUseCase parsingUseCase;

    public ScenarioCatalogService(ScenarioCatalogProperties properties,
                                  ScenarioParsingUseCase parsingUseCase) {
        this.properties = properties;
        this.parsingUseCase = parsingUseCase;
    }

    /**
     * Scanne le repertoire configure et retourne les scenarios tries par nom de fichier.
     * Retourne une liste vide si le repertoire n'est pas configure ou n'existe pas.
     *
     * @return les entrees du catalogue (valides et invalides)
     */
    public List<CatalogScenario> list() {
        String directory = properties.directory();
        if (directory == null || directory.isBlank()) {
            return List.of();
        }

        Path root = Path.of(directory);
        if (!Files.isDirectory(root)) {
            log.warn("action=scenario_catalog_missing directory={}", directory);
            return List.of();
        }

        try (Stream<Path> files = Files.list(root)) {
            List<CatalogScenario> scenarios = files
                    .filter(Files::isRegularFile)
                    .filter(ScenarioCatalogService::isYaml)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::load)
                    .toList();
            log.info("action=scenario_catalog_loaded directory={} count={}",
                    directory, scenarios.size());
            return scenarios;
        } catch (IOException e) {
            log.error("action=scenario_catalog_read_failed directory={} message={}",
                    directory, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Recherche une entree par sa cle (nom de fichier).
     *
     * @param key nom du fichier
     * @return l'entree si elle existe
     */
    public Optional<CatalogScenario> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return list().stream()
                .filter(scenario -> scenario.key().equals(key))
                .findFirst();
    }

    private CatalogScenario load(Path path) {
        String key = path.getFileName().toString();
        String yaml;
        try {
            yaml = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("action=scenario_catalog_file_unreadable file={} message={}", key, e.getMessage());
            return new CatalogScenario(key, null, null, "Unable to read file: " + e.getMessage());
        }

        try {
            var definition = parsingUseCase.parse(yaml);
            log.debug("action=scenario_catalog_parsed file={} scenarioId={}",
                    key, definition.id().value());
            return new CatalogScenario(key, yaml, definition, null);
        } catch (RuntimeException e) {
            log.warn("action=scenario_catalog_parse_failed file={} message={}", key, e.getMessage());
            return new CatalogScenario(key, yaml, null, e.getMessage());
        }
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }
}
