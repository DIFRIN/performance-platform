package com.performance.platform.engine.remote;

import com.performance.platform.domain.execution.ExecutionContext;
import com.performance.platform.domain.execution.PartialExecutionContext;
import com.performance.platform.domain.task.TaskResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Construit un {@link PartialExecutionContext} a partir de
 * l'{@link ExecutionContext} complet en extrayant uniquement les entrees
 * listees dans {@code requiredContextKeys}.
 *
 * <p>Pour chaque cle de contexte requise, les TaskResult sont visites et
 * leurs outputs sont extraits. Le resultat est un sous-ensemble immuable du
 * contexte complet, pret a etre transmis aux agents via le transport.</p>
 *
 * <p>0 annotation framework — classe Java pure.</p>
 */
public final class PartialContextBuilder {

    private PartialContextBuilder() {
        // Classe utilitaire, pas d'instanciation
    }

    /**
     * Construit un {@link PartialExecutionContext} contenant uniquement les
     * entrees designees par {@code requiredContextKeys}.
     *
     * <p>Pour chaque cle requise, parcourt les resultats de tous les agents
     * dans le {@link ExecutionContext} et extrait la premiere valeur des
     * {@link TaskResult#outputs()}. Si une cle est absente du contexte,
     * elle est ignoree silencieusement (le store partiel ne contiendra
     * aucune entree pour cette cle).</p>
     *
     * @param fullContext         le contexte d'execution complet
     * @param requiredContextKeys les cles de contexte requises par l'etape
     * @return un contexte partiel contenant uniquement les entrees demandees
     */
    public static PartialExecutionContext build(
            ExecutionContext fullContext,
            Set<String> requiredContextKeys) {

        if (fullContext == null || requiredContextKeys == null || requiredContextKeys.isEmpty()) {
            if (fullContext == null) {
                return new PartialExecutionContext(null, null, Map.of());
            }
            return PartialExecutionContext.empty(fullContext.executionId(), fullContext.scenarioId());
        }

        var partialStore = new HashMap<String, Map<String, Object>>(requiredContextKeys.size());

        for (String key : requiredContextKeys) {
            Map<String, TaskResult> agentResults = fullContext.store().get(key);
            if (agentResults == null || agentResults.isEmpty()) {
                continue;
            }

            var extractedOutputs = new HashMap<String, Object>(agentResults.size());
            for (var entry : agentResults.entrySet()) {
                TaskResult result = entry.getValue();
                if (result.outputs() != null && !result.outputs().isEmpty()) {
                    // Extraire la premiere valeur des outputs comme valeur principale
                    Object firstOutput = result.outputs().values().iterator().next();
                    extractedOutputs.put(entry.getKey(), firstOutput);
                }
            }

            if (!extractedOutputs.isEmpty()) {
                partialStore.put(key, Map.copyOf(extractedOutputs));
            }
        }

        return new PartialExecutionContext(
                fullContext.executionId(),
                fullContext.scenarioId(),
                Map.copyOf(partialStore));
    }
}
