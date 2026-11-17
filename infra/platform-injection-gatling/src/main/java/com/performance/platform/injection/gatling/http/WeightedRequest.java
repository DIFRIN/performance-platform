package com.performance.platform.injection.gatling.http;

import java.util.Objects;

/** One named request (HTTP spec or Kafka message template) of a {@link RequestMix} with its (non-normalized) positive weight. */
public record WeightedRequest<S>(String name, double weight, S spec) {

    public WeightedRequest {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        if (!(weight > 0) || Double.isInfinite(weight)) {
            throw new IllegalArgumentException("weight must be strictly positive: " + weight);
        }
    }
}
