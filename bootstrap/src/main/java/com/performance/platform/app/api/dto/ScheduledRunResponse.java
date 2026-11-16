package com.performance.platform.app.api.dto;

/**
 * Representation d'un run planifie pour l'IHM.
 *
 * @param scheduleId      identifiant du run planifie
 * @param scenarioId      identifiant du scenario
 * @param kind            {@code ONCE} ou {@code CRON}
 * @param schedule        expression de planification
 * @param nextRunAt       prochaine occurrence ISO-8601
 * @param status          {@code PENDING}, {@code COMPLETED}, {@code FAILED} ou {@code CANCELLED}
 * @param lastExecutionId derniere execution declenchee, si disponible
 * @param createdAt       date de creation ISO-8601
 * @param updatedAt       date de mise a jour ISO-8601
 */
public record ScheduledRunResponse(String scheduleId, String scenarioId, String kind,
                                   String schedule, String nextRunAt, String status,
                                   String lastExecutionId, String createdAt, String updatedAt) {
}
