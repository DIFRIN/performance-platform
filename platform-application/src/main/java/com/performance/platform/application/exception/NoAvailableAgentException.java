package com.performance.platform.application.exception;

/**
 * Exception levee lorsqu'aucun agent n'est disponible pour executer une tache.
 */
public class NoAvailableAgentException extends RuntimeException {

    public NoAvailableAgentException(String taskName) {
        super("No available agent for task: " + taskName);
    }
}
