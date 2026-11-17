package com.performance.platform.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.performance.platform.domain.injection.ArrivalModel;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InjectionCapabilitiesTest {

    @Test
    void copiesSetsDefensively() {
        Set<ArrivalModel> models = new HashSet<>(Set.of(ArrivalModel.OPEN));
        Set<String> kinds = new HashSet<>(Set.of("ramp"));
        Set<String> protocols = new HashSet<>(Set.of("http"));
        var caps = new InjectionCapabilities(models, kinds, protocols, true, false, true, false);

        models.add(ArrivalModel.CLOSED);
        kinds.add("burst");
        protocols.add("grpc");

        assertThat(caps.models()).containsExactly(ArrivalModel.OPEN);
        assertThat(caps.stageKinds()).containsExactly("ramp");
        assertThat(caps.protocols()).containsExactly("http");
        assertThat(caps.supportsGlobalScope()).isTrue();
        assertThat(caps.providesHistogram()).isTrue();
    }

    @Test
    void exposedSetsAreImmutable() {
        var caps = new InjectionCapabilities(Set.of(), Set.of(), Set.of(), false, false, false, false);
        assertThatThrownBy(() -> caps.stageKinds().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullSets() {
        assertThatThrownBy(() -> new InjectionCapabilities(null, Set.of(), Set.of(), false, false, false, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void providerReturnsCapabilities() {
        var caps = new InjectionCapabilities(Set.of(ArrivalModel.CLOSED), Set.of("hold"), Set.of("http"),
                false, true, false, true);
        InjectionCapabilityProvider provider = () -> caps;
        assertThat(provider.injectionCapabilities()).isSameAs(caps);
    }
}
