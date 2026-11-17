package com.performance.platform.injection.gatling.http;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestMixTest {

    private static HttpRequestSpec spec(String id) {
        return new HttpRequestSpec("e", id, "GET", "http://x", "/", Map.of(), Map.of(), Map.of(), null, 0);
    }

    private static RequestMix mix() {
        return new RequestMix(List.of(
                new WeightedRequest("create", 6, spec("a")),
                new WeightedRequest("read", 3, spec("b")),
                new WeightedRequest("delete", 1, spec("c"))));
    }

    @Test
    void distributionMatchesWeightsWithinTwoPercent() {
        RequestMix mix = mix();
        RandomGenerator rnd = RequestMix.generator(42L);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 10_000; i++) counts.merge(mix.pick(rnd).name(), 1, Integer::sum);
        for (WeightedRequest r : mix.requests()) {
            assertThat(counts.get(r.name()) / 10_000.0).isCloseTo(mix.normalizedWeight(r.name()),
                    org.assertj.core.data.Offset.offset(0.02));
        }
    }

    @Test
    void sameSeedSameSequence() {
        RequestMix mix = mix();
        assertThat(sequence(mix, 7L)).isEqualTo(sequence(mix, 7L)).isNotEqualTo(sequence(mix, 8L));
    }

    @Test
    void singleIsNamedDefault() {
        RequestMix single = RequestMix.single(spec("a"));
        assertThat(single.requests()).extracting(WeightedRequest::name).containsExactly("default");
        assertThat(single.normalizedWeight("default")).isEqualTo(1.0);
    }

    @Test
    void rejectsInvalid() {
        assertThatThrownBy(() -> new WeightedRequest("a", 0, spec("a"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestMix(List.of(new WeightedRequest("a", 1, spec("a")),
                new WeightedRequest("a", 1, spec("b"))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mix().normalizedWeight("nope")).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> sequence(RequestMix mix, long seed) {
        RandomGenerator rnd = RequestMix.generator(seed);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 200; i++) out.add(mix.pick(rnd).name());
        return out;
    }
}
