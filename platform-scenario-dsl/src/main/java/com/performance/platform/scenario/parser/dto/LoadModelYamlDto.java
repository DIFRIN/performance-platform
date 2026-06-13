package com.performance.platform.scenario.parser.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTO pour un load model dans le YAML.
 * Le champ "type" est extrait ; tous les autres champs vont dans parameters.
 * DTO interne — jamais expose hors du module.
 */
public class LoadModelYamlDto {

    private String type;
    private final Map<String, Object> parameters = new LinkedHashMap<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    @JsonAnySetter
    public void setParameter(String key, Object value) {
        if (!"type".equals(key)) {
            parameters.put(key, value);
        }
    }
}
