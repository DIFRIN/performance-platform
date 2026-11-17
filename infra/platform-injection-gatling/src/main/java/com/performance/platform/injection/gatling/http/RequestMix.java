package com.performance.platform.injection.gatling.http;

import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

/** Weighted set of requests; each virtual user sends exactly one, drawn by weight (ADR-070). */
public record RequestMix(List<WeightedRequest> requests) {

    public static final String DEFAULT_NAME = "default";

    public RequestMix {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("requests must not be empty");
        }
        requests = List.copyOf(requests);
        long distinct = requests.stream().map(WeightedRequest::name).distinct().count();
        if (distinct != requests.size()) {
            throw new IllegalArgumentException("request names must be unique");
        }
    }

    /** Current single-request form = mix of one request named {@code default}. */
    public static RequestMix single(HttpRequestSpec spec) {
        return new RequestMix(List.of(new WeightedRequest(DEFAULT_NAME, 1.0, spec)));
    }

    public double normalizedWeight(String name) {
        double total = requests.stream().mapToDouble(WeightedRequest::weight).sum();
        return requests.stream().filter(r -> r.name().equals(name)).findFirst()
                .map(r -> r.weight() / total)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request name: " + name));
    }

    /** Deterministic draw: same generator state => same request. */
    public WeightedRequest pick(RandomGenerator random) {
        double total = requests.stream().mapToDouble(WeightedRequest::weight).sum();
        double x = random.nextDouble() * total;
        double acc = 0;
        for (WeightedRequest r : requests) {
            acc += r.weight();
            if (x < acc) return r;
        }
        return requests.getLast();
    }

    /** Seeded generator for a share (seed = plan hash ^ share index, see ShareCounter#seed). */
    public static RandomGenerator generator(long seed) {
        return new Random(seed);
    }
}
