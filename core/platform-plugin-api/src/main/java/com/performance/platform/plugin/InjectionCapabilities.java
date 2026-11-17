package com.performance.platform.plugin;

import com.performance.platform.domain.injection.ArrivalModel;
import java.util.Set;

/**
 * Capacites declarees par un adaptateur d'injection. Reference : ADR-065.
 *
 * @param stageKinds kinds de stage supportes ("ramp", "hold", "burst", "pause")
 */
public record InjectionCapabilities(
        Set<ArrivalModel> models,
        Set<String> stageKinds,
        Set<String> protocols,
        boolean supportsGlobalScope,
        boolean supportsCancellation,
        boolean providesHistogram,
        boolean providesWindows) {

    public InjectionCapabilities {
        models = Set.copyOf(models);
        stageKinds = Set.copyOf(stageKinds);
        protocols = Set.copyOf(protocols);
    }
}
