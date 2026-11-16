package com.performance.platform.domain.injection;

import java.time.Duration;

public record Ramp(double from, double to, Duration duration, boolean measured) implements Stage {
    public Ramp {
        Stage.requireLevel(from, "from");
        Stage.requireLevel(to, "to");
        Stage.requirePositive(duration, "duration");
    }
}
