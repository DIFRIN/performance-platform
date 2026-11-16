package com.performance.platform.probe.model;

/** Point d'une serie : timestamp epoch millis + valeur. */
public record ProbePoint(long timestamp, double value) {
}
