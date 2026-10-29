package com.performance.platform.application.ports.in;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.scenario.ScenarioDefinition;

/**
 * Use case : parser un contenu YAML en ScenarioDefinition.
 * L'exception ScenarioParsingException est definie dans le module application
 * pour que le port n'importe pas le module scenario-dsl.
 */
public interface ScenarioParsingUseCase {

    ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException;
}
