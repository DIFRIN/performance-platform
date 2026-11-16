package com.performance.platform.probe.model;

import java.time.Instant;

/** Fenetre demandee, resolue en instants. */
public record ProbeWindow(Instant from, Instant to, long resolutionMillis) {

    public long fromEpochMillis() {
        return from.toEpochMilli();
    }

    public long toEpochMillis() {
        return to.toEpochMilli();
    }

    public ProbeWindow withMargins(java.time.Duration before, java.time.Duration after) {
        java.time.Duration b = before == null ? java.time.Duration.ZERO : before;
        java.time.Duration a = after == null ? java.time.Duration.ZERO : after;
        return new ProbeWindow(from.minus(b), to.plus(a), resolutionMillis);
    }
}
