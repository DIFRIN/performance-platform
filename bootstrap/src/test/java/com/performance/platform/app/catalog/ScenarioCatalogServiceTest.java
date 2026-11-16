package com.performance.platform.app.catalog;

import com.performance.platform.application.ports.in.ScenarioParsingUseCase;
import com.performance.platform.domain.id.ScenarioId;
import com.performance.platform.domain.id.TaskId;
import com.performance.platform.domain.scenario.Phase;
import com.performance.platform.domain.scenario.ScenarioDefinition;
import com.performance.platform.domain.scenario.StepDefinition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ScenarioCatalogService")
class ScenarioCatalogServiceTest {

    private final ScenarioParsingUseCase parsingUseCase = mock(ScenarioParsingUseCase.class);

    @TempDir
    Path directory;

    @Test
    @DisplayName("should return an empty catalog when no directory is configured")
    void shouldReturnEmptyWhenDirectoryNotConfigured() {
        var service = new ScenarioCatalogService(new ScenarioCatalogProperties(""), parsingUseCase);

        assertThat(service.list()).isEmpty();
    }

    @Test
    @DisplayName("should return an empty catalog when the directory does not exist")
    void shouldReturnEmptyWhenDirectoryMissing() {
        var service = new ScenarioCatalogService(
                new ScenarioCatalogProperties(directory.resolve("missing").toString()), parsingUseCase);

        assertThat(service.list()).isEmpty();
    }

    @Test
    @DisplayName("should list yaml and yml files sorted, ignoring other files")
    void shouldListYamlFilesSorted() throws IOException {
        write("b.yaml", "b");
        write("a.yml", "a");
        write("notes.txt", "ignored");
        when(parsingUseCase.parse("a")).thenReturn(definition("a", "a.yml"));
        when(parsingUseCase.parse("b")).thenReturn(definition("b", "b.yaml"));

        var service = new ScenarioCatalogService(
                new ScenarioCatalogProperties(directory.toString()), parsingUseCase);

        var entries = service.list();

        assertThat(entries).extracting(CatalogScenario::key)
                .containsExactly("a.yml", "b.yaml");
        assertThat(entries).allMatch(CatalogScenario::valid);
        assertThat(entries.get(0).definition().steps()).hasSize(1);
    }

    @Test
    @DisplayName("should expose invalid files with their parse error instead of failing")
    void shouldExposeParseErrors() throws IOException {
        write("broken.yaml", "broken");
        when(parsingUseCase.parse("broken")).thenThrow(new RuntimeException("scenario.id is required"));

        var service = new ScenarioCatalogService(
                new ScenarioCatalogProperties(directory.toString()), parsingUseCase);

        var entries = service.list();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).valid()).isFalse();
        assertThat(entries.get(0).error()).contains("scenario.id is required");
        assertThat(entries.get(0).yaml()).isEqualTo("broken");
    }

    @Test
    @DisplayName("should find an entry by its file name")
    void shouldFindByKey() throws IOException {
        write("scenario.yaml", "valid");
        when(parsingUseCase.parse("valid")).thenReturn(definition("scenario", "scenario.yaml"));
        var service = new ScenarioCatalogService(
                new ScenarioCatalogProperties(directory.toString()), parsingUseCase);

        assertThat(service.find("scenario.yaml")).isPresent();
        assertThat(service.find("missing.yaml")).isEmpty();
        assertThat(service.find(null)).isEmpty();
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(directory.resolve(fileName), content);
    }

    private static ScenarioDefinition definition(String scenarioId, String yaml) {
        var step = new StepDefinition(
                TaskId.of("prep"),
                "mock-server",
                Phase.PREPARATION,
                Map.of("port", 8090),
                List.of(),
                List.of(),
                Duration.ofSeconds(30),
                null);
        return new ScenarioDefinition(
                ScenarioId.of(scenarioId),
                "Scenario " + scenarioId,
                "1.0.0",
                List.of("test"),
                Map.of(),
                List.of(step),
                yaml,
                null);
    }
}
