package com.performance.platform.domain.injection;

import java.time.Duration;

public record Hold(double level, Duration duration, boolean measured) implements Stage {
    public Hold {
        Stage.requireLevel(level, "level");
        Stage.requirePositive(duration, "duration");
    }
}
