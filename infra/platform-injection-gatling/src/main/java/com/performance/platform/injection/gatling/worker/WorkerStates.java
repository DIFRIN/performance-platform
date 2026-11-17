package com.performance.platform.injection.gatling.worker;

import com.performance.platform.injection.gatling.http.HttpWorkerState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Envelope {@code {type, data}} of the protocol state carried by the run directory. */
public final class WorkerStates {

    static final List<WorkerStateRestorer> RESTORERS = List.of(new HttpWorkerState());

    private WorkerStates() {
    }

    public static Map<String, Object> envelope(String type, Map<String, Object> data) {
        var m = new LinkedHashMap<String, Object>();
        m.put("type", type);
        m.put("data", data);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static void restore(String simulationId, Map<String, Object> state) {
        if (state == null || state.isEmpty()) return;
        Object type = state.get("type");
        var restorer = RESTORERS.stream().filter(r -> r.type().equals(type)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No worker state restorer for type '" + type + "'"));
        restorer.restore(simulationId, (Map<String, Object>) state.get("data"));
    }
}
