package com.performance.platform.transport;

import com.performance.platform.transport.message.TaskExecutionRequest;

/**
 * Handler appele par le transport lorsqu'une {@link TaskExecutionRequest}
 * est recue. Le handler est enregistre cote agent via
 * {@link ExecutionTransport#receiveTask(TaskRequestHandler)}.
 */
@FunctionalInterface
public interface TaskRequestHandler {

    /**
     * Appele a la reception d'une demande d'execution de task.
     *
     * @param request la demande d'execution recue
     */
    void onRequest(TaskExecutionRequest request);
}
