package com.performance.platform.domain.assertion;

/** Nature d'une metrique du catalogue. */
public enum MetricKind {
    COUNTER,
    GAUGE,
    RATIO,
    QUANTILE,
    DURATION,
    STATE
}
