package com.performance.platform.domain.injection;

import java.time.Duration;

public record Burst(long users, boolean measured) implements Stage {
    public Burst {
        if (users <= 0) {
            throw new IllegalArgumentException("users must be > 0");
        }
    }

    @Override
    public Duration duration() {
        return Duration.ZERO;
    }
}
