package com.performance.platform.application.schedule;

/**
 * Nature d'une planification de run.
 * <ul>
 *   <li>{@code IMMEDIATE} — aucun delai, execution des la soumission</li>
 *   <li>{@code ONCE} — execution unique a un instant donne</li>
 *   <li>{@code CRON} — executions repetees selon une expression cron</li>
 * </ul>
 * 0 annotation framework.
 */
public enum ScheduleKind {
    IMMEDIATE,
    ONCE,
    CRON
}
