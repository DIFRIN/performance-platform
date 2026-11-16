package com.performance.platform.app.api.dto;

/**
 * Resultat d'une soumission via {@code POST /api/v1/schedules}.
 *
 * @param executionId identifiant d'execution pour un run immediat, sinon {@code null}
 * @param scheduleId  identifiant du run planifie pour un run differe, sinon {@code null}
 * @param status      {@code ACCEPTED} (immediat) ou {@code SCHEDULED} (differe)
 * @param nextRunAt   prochaine occurrence ISO-8601 pour un run differe, sinon {@code null}
 */
public record ScheduleSubmissionResponse(String executionId, String scheduleId,
                                         String status, String nextRunAt) {
}
