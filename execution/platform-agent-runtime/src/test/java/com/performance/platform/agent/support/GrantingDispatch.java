package com.performance.platform.agent.support;

import com.performance.platform.domain.event.TaskGrantSignal;
import com.performance.platform.domain.execution.GrantShare;
import com.performance.platform.domain.id.SignalId;
import com.performance.platform.transport.ExecutionTransport;
import com.performance.platform.transport.message.ExecutionEvent;
import com.performance.platform.transport.message.TaskExecutionRequest;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;

/**
 * Dispatch avec grant automatique (ADR-055).
 * <p>
 * Un agent ne fait que revendiquer une tache ; il ne l'execute qu'apres
 * reception d'un {@link TaskGrantSignal} qui le nomme. Cette utilite simule
 * l'orchestrateur : elle accorde le grant au premier agent qui revendique le
 * message dispatche, puis rend la main.
 */
public final class GrantingDispatch {

    private GrantingDispatch() {
    }

    public static void dispatchAndGrant(ExecutionTransport transport, TaskExecutionRequest request) {
        dispatchAndGrant(transport, request, GrantShare.single());
    }

    public static void dispatchAndGrant(ExecutionTransport transport, TaskExecutionRequest request,
                                        GrantShare share) {
        var granted = new AtomicBoolean();
        var subscription = transport.subscribe(event -> {
            if (ExecutionEvent.TASK_CLAIMED.equals(event.eventType())
                    && request.id().equals(event.correlationId())
                    && event.agentId() != null
                    && granted.compareAndSet(false, true)) {
                transport.broadcastSignal(new TaskGrantSignal(SignalId.generate(),
                        request.executionId(), request.step().id(), request.id(),
                        event.agentId(), true, Instant.now(), share));
            }
        });
        try {
            transport.dispatchTask(request);
            await().atMost(5, TimeUnit.SECONDS).untilTrue(granted);
        } finally {
            subscription.cancel();
        }
    }
}
