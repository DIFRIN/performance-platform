package com.performance.platform.domain.injection;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Plan de charge typé, immuable, indépendant du moteur d'injection. */
public record WorkloadPlan(ArrivalModel model, LoadScope scope, List<Stage> stages) {
    public WorkloadPlan {
        Objects.requireNonNull(model, "model required");
        Objects.requireNonNull(scope, "scope required");
        Objects.requireNonNull(stages, "stages required");
        stages = List.copyOf(stages);
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }
    }

    /** Somme des durées des étapes. */
    public Duration injectionWindow() {
        return stages.stream().map(Stage::duration).reduce(Duration.ZERO, Duration::plus);
    }
}
