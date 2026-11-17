package com.performance.platform.reporting.engine;

import com.performance.platform.assertion.IntegrityCheckFactory;
import com.performance.platform.assertion.IntegrityInput;
import com.performance.platform.domain.assertion.Check;
import com.performance.platform.domain.assertion.IntegrityTolerances;
import com.performance.platform.domain.execution.ExecutionState;
import com.performance.platform.domain.execution.ExecutionTask;
import com.performance.platform.domain.injection.InjectionResult;
import com.performance.platform.domain.injection.LoadWindow;
import com.performance.platform.domain.task.TaskResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Genere les controles d'integrite (ADR-069) de chaque etape d'injection ayant produit
 * au moins un resultat. Une etape sans resultat (plan rejete, minAgents non atteint)
 * n'est pas evaluee : l'execution est FAILED, pas INVALID.
 */
final class IntegrityChecks {

    static final String BLOCK_KEY = "integrity";

    private IntegrityChecks() {}

    /** @return controles par id d'etape, dans l'ordre d'apparition. */
    static Map<String, List<Check>> forState(ExecutionState state) {
        Map<String, List<Check>> out = new LinkedHashMap<>();
        for (var entry : state.context().store().entrySet()) {
            String stepId = entry.getKey();
            List<InjectionResult> results = entry.getValue().values().stream()
                    .map(r -> r.outputs().get(InjectionResult.OUTPUT_KEY))
                    .filter(InjectionResult.class::isInstance).map(InjectionResult.class::cast).toList();
            if (results.isEmpty()) continue;
            Map<String, Object> config = state.tasks().stream()
                    .filter(t -> t.id().value().equals(stepId)).findFirst()
                    .map(ExecutionTask::configuration).orElse(Map.of());
            out.put(stepId, IntegrityCheckFactory.checks(
                    input(results, entry.getValue().size()), tolerances(config)));
        }
        return out;
    }

    static IntegrityInput input(List<InjectionResult> results, int grantedAgents) {
        long planned = 0;
        long started = 0;
        boolean complete = true;
        double cpu = 0.0;
        for (InjectionResult r : results) {
            for (LoadWindow w : r.windows()) {
                if (!w.measured()) continue;
                planned += w.plannedArrivals();
                started += w.startedUsers();
            }
            // Un resultat sans fenetre est un resultat historique : aucune preuve d'arret anticipe.
            complete &= r.complete() || r.windows().isEmpty();
            cpu = Math.max(cpu, r.generatorHealth().cpuLoad());
        }
        boolean known = planned > 0;
        double deviation = known ? Math.abs(planned - started) * 100.0 / planned : Double.NaN;
        // cpuLoad = coeurs utilises ; 0 = non mesure.
        double cpuPercent = cpu <= 0.0 ? Double.NaN
                : cpu / Runtime.getRuntime().availableProcessors() * 100.0;
        return new IntegrityInput(deviation, known, Math.max(grantedAgents, results.size()),
                results.size(), complete, cpuPercent);
    }

    static IntegrityTolerances tolerances(Map<String, Object> parameters) {
        var defaults = IntegrityTolerances.defaults();
        if (!(parameters.get(BLOCK_KEY) instanceof Map<?, ?> block)) return defaults;
        try {
            return new IntegrityTolerances(
                    percent(block.get("maxArrivalDeviation"), defaults.maxArrivalDeviationPercent()),
                    percent(block.get("maxGeneratorCpu"), defaults.maxGeneratorCpuPercent()));
        } catch (IllegalArgumentException invalid) {
            return defaults; // deja rejete par la validation du scenario
        }
    }

    private static double percent(Object raw, double fallback) {
        if (raw instanceof Number n) return n.doubleValue();
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.endsWith("%")) t = t.substring(0, t.length() - 1).trim();
            return Double.parseDouble(t);
        }
        return fallback;
    }
}
