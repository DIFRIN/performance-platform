package com.performance.platform.infrastructure.executor;

/**
 * Levee quand aucun {@link TaskExecutor} n'est enregistre pour un {@code taskName} donne.
 * RuntimeException — cette situation est une erreur de configuration, pas un cas metier.
 */
public class UnsupportedTaskNameException extends RuntimeException {

    private final String taskName;

    public UnsupportedTaskNameException(String taskName) {
        super("No TaskExecutor registered for taskName: " + taskName);
        this.taskName = taskName;
    }

    /**
     * @return le taskName pour lequel aucun executor n'a ete trouve
     */
    public String getTaskName() {
        return taskName;
    }
}
