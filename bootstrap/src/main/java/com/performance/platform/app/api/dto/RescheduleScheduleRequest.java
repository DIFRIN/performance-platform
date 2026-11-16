package com.performance.platform.app.api.dto;

/**
 * Requete de replanification d'un run planifie.
 *
 * @param schedule nouvelle expression ({@code in 10m}, date ISO-8601,
 *                 {@code cron:0 0 2 * * *})
 */
public record RescheduleScheduleRequest(String schedule) {
}
