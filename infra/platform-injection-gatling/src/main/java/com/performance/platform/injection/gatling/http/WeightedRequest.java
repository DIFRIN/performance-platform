package com.performance.platform.injection.gatling.http;

import java.util.Objects;

/** One named request of a {@link RequestMix} with its (non-normalized) positive weight. */
public record WeightedRequest(String name, double weight, HttpRequestSpec spec) {

    public WeightedRequest {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        if (!(weight > 0) || Double.isInfinite(weight)) {
            throw new IllegalArgumentException("weight must be strictly positive: " + weight);
        }
    }
}
