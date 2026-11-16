package com.performance.platform.probe.model;

import java.util.List;

/** Serie temporelle retournee par une source (contrat JSON du CLI). */
public record ProbeSeries(String metric, String unit, List<ProbePoint> points) {

    public ProbeSeries {
        metric = metric == null ? "panel.series.A" : metric;
        unit = unit == null ? "" : unit;
        points = points == null ? List.of() : List.copyOf(points);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }
}
