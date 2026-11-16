package com.performance.platform.app.api.dto;

/**
 * Demande de run immediat ou planifie.
 *
 * @param scenarioYaml YAML du scenario a executer
 * @param schedule     expression de planification ({@code null}/vide = immediat)
 */
public record ScheduleSubmissionRequest(String scenarioYaml, String schedule) {
}
