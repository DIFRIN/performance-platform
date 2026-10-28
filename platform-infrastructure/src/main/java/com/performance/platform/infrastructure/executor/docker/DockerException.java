package com.performance.platform.infrastructure.executor.docker;

/**
 * Unchecked exception for Docker operation failures.
 * <p>
 * Package-private: only thrown by {@link DockerClient} implementations
 * and caught by {@link DockerTaskExecutor}.
 */
class DockerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    DockerException(String message) {
        super(message);
    }

    DockerException(String message, Throwable cause) {
        super(message, cause);
    }
}
