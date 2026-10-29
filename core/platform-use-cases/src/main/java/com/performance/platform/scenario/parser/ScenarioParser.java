package com.performance.platform.scenario.parser;

import com.performance.platform.application.exception.ScenarioParsingException;
import com.performance.platform.domain.scenario.ScenarioDefinition;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Port entrant : parser un contenu YAML en ScenarioDefinition immuable.
 * L'implementation utilise SnakeYAML pour le parsing brut et Jackson pour le mapping DTO.
 */
public interface ScenarioParser {

    /**
     * Parse un flux YAML en ScenarioDefinition.
     *
     * @param yamlContent le flux YAML (ne sera pas ferme par cette methode)
     * @return la definition de scenario parsee
     * @throws ScenarioParsingException si le YAML est malforme ou contient des erreurs de parsing
     */
    ScenarioDefinition parse(InputStream yamlContent) throws ScenarioParsingException;

    /**
     * Parse une chaine YAML en ScenarioDefinition.
     *
     * @param yamlContent la chaine YAML
     * @return la definition de scenario parsee
     * @throws ScenarioParsingException si le YAML est malforme ou contient des erreurs de parsing
     */
    ScenarioDefinition parse(String yamlContent) throws ScenarioParsingException;

    /**
     * Parse un fichier YAML en ScenarioDefinition.
     *
     * @param scenarioFile le chemin du fichier YAML
     * @return la definition de scenario parsee
     * @throws ScenarioParsingException si le YAML est malforme ou contient des erreurs de parsing
     */
    ScenarioDefinition parseFile(Path scenarioFile) throws ScenarioParsingException;
}
