package com.performance.platform.domain.injection;

import java.time.Duration;

public record Pause(Duration duration, boolean measured) implements Stage {
    public Pause {
        Stage.requirePositive(duration, "duration");
    }
}
